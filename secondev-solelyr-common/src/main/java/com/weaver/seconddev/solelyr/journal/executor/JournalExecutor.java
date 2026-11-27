package com.weaver.seconddev.solelyr.journal.executor;

import com.weaver.seconddev.solelyr.journal.exception.JournalException;
import com.weaver.seconddev.solelyr.journal.entity.JournalResult;
import com.weaver.seconddev.solelyr.journal.entity.JournalUnit;
import com.weaver.seconddev.solelyr.journal.exception.JournalErrors;
import com.weaver.seconddev.solelyr.journal.service.JournalService;
import com.weaver.common.distribution.lock.DistributionLockInterface;
import com.weaver.seconddev.solelyr.workflow.exception.WorkflowEsbException;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.io.EOFException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Function;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.Arrays;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;

/**
 * @USER: solelyr
 * @DATE: 2026/11/22 21:57
 * @DESCRIPTION: 负责单笔业务调用的占用、执行、留痕与成功结果复用
 */
public final class JournalExecutor {
    /** 按当前业务单元创建 OA 日志及幂等记录的访问上下文。 */
    private final Function<JournalUnit, JournalService> journalFactory;
    /** 获取 E10 分布式锁组件，协调多个二开实例的同键调用。 */
    private final Supplier<DistributionLockInterface> lockProvider;

    /**
     * 创建供流程动作共用的第三方接口调用执行器。
     *
     * @param journalFactory 按调用单元创建幂等状态表和调用日志表的访问对象
     * @param lockProvider 提供各二开实例共用的 E10 分布式锁服务
     */
    public JournalExecutor(Function<JournalUnit, JournalService> journalFactory, Supplier<DistributionLockInterface> lockProvider) {
        this.journalFactory = journalFactory;
        this.lockProvider = lockProvider;
    }

    /**
     * 执行返回文本的接口。命中幂等记录时原样返回已保存的文本。
     *
     * @param actionId 本次流程动作的执行编号，用于关联该动作产生的调用日志
     * @param unit 本笔推送的业务上下文及幂等键
     * @param sendRequest 查无幂等记录时执行的第三方请求
     * @return 本次取得或先前保存的业务回执文本
     */
    public String execute(String actionId, JournalUnit unit, Supplier<JournalResult<String>> sendRequest) {
        return execute(actionId, unit, Function.identity(), sendRequest);
    }

    /**
     * 执行指定回执类型的接口。命中幂等记录时使用 Fastjson2 还原业务对象，
     * 字符串类型则直接取保存值。
     *
     * @param <T> 接口的业务回执类型
     * @param actionId 本次流程动作的执行编号
     * @param unit 本笔推送的业务上下文及幂等键
     * @param resultType 回执类型，例如供应商接口使用的 {@code U8Response.class}
     * @param sendRequest 查无幂等记录时执行的第三方请求
     * @return 本次取得或从幂等记录还原的业务回执
     */
    public <T> T execute(String actionId, JournalUnit unit, Class<T> resultType, Supplier<JournalResult<T>> sendRequest) {
        return execute(actionId, unit, stored -> {
            if (resultType == String.class) {
                return resultType.cast(stored);
            }
            try {
                T result = JSON.parseObject(stored, resultType);
                if (result != null) {
                    return result;
                }
            } catch (JSONException invalid) {
                throw new WorkflowEsbException(JournalErrors.VERIFY, "已保存的接口回执无法还原，请核对成功记录");
            }
            throw new WorkflowEsbException(JournalErrors.VERIFY, "已保存的接口回执为空，请核对成功记录");
        }, sendRequest);
    }

    /**
     * 在同一把分布式锁内按幂等记录决定复用回执或实际发送请求。
     *
     * @param <T> 接口的业务回执类型
     * @param actionId 本次流程动作的执行编号
     * @param unit 本笔推送的业务上下文及幂等键
     * @param restoreResult 命中幂等记录时，将成功结果字段的文本还原为业务回执的函数
     * @param sendRequest 查无幂等记录时执行的第三方请求
     * @return 还原的回执或本次请求取得的回执
     */
    public <T> T execute(String actionId, JournalUnit unit, Function<String, T> restoreResult,
                         Supplier<JournalResult<T>> sendRequest) {
        JournalService journal = journalFactory.apply(unit);
        Supplier<T> attempt = () -> executeAttempt(actionId, journal, restoreResult, sendRequest);
        return unit.manageIdempotency ? withCallLock(actionId, unit, journal, attempt) : attempt.get();
    }

    /**
     * 按租户、目标系统、接口操作和幂等键取得分布式锁，任务结束后释放锁。
     * 若任务执行和释放锁均失败，将释放锁异常附加到原异常。
     *
     * @param <T> 锁内任务的返回类型
     * @param unit 确定锁作用范围的调用单元
     * @param task 取得锁后执行的任务
     * @return 锁内任务的结果
     * @throws WorkflowEsbException 同键调用正在执行，或获取、释放锁时发生故障
     */
    private <T> T withCallLock(String actionId, JournalUnit unit, JournalService journal, Supplier<T> task) {
        String key = "workflowpush:" + JSON.toJSONString(Arrays.asList(unit.tenant, unit.system, unit.operation, unit.key));
        long startedAt = System.currentTimeMillis();
        DistributionLockInterface lock;
        boolean acquired;
        try {
            lock = lockProvider.get();
            acquired = lock.tryLock(key);
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            WorkflowEsbException error = new WorkflowEsbException(JournalErrors.STORAGE,
                    "无法取得 OA 分布式锁：" + lockFailureReason(failure), failure);
            journal.recordUnsentFailure(actionId, "LOCK_ACQUIRE", startedAt, error);
            throw error;
        }
        if (!acquired) {
            WorkflowEsbException busy = new WorkflowEsbException(JournalErrors.BUSY, "该笔推送正在处理，请稍后重新提交");
            journal.recordUnsentFailure(actionId, "LOCK_ACQUIRE", startedAt, busy);
            throw busy;
        }
        Throwable originalFailure = null;
        try {
            return task.get();
        } catch (RuntimeException | Error failure) {
            originalFailure = failure;
            throw failure;
        } finally {
            try {
                lock.unLock(key);
            } catch (Exception unlockFailure) {
                if (unlockFailure instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (originalFailure == null) {
                    throw new WorkflowEsbException(JournalErrors.STORAGE, "OA 分布式锁释放失败", unlockFailure);
                }
                originalFailure.addSuppressed(unlockFailure);
            }
        }
    }

    /** 根据加锁异常链生成业务提示，只返回已识别的原因。 */
    private static String lockFailureReason(Exception failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean redisConnection = false;
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof InterruptedException) {
                return "加锁等待被中断";
            }
            if (cause instanceof JedisConnectionException) {
                redisConnection = true;
                if ("Unexpected end of stream.".equals(cause.getMessage())) {
                    return "Redis 加锁响应异常，读取响应时连接已断开";
                }
            }
            if (redisConnection) {
                if (cause instanceof EOFException) {
                    return "Redis 加锁响应异常，读取响应时连接已断开";
                }
                if (cause instanceof SocketException && cause.getMessage() != null
                        && cause.getMessage().startsWith("Broken pipe")) {
                    return "写入 Redis 加锁命令失败，连接已断开";
                }
                if (cause instanceof SocketTimeoutException) {
                    return "Redis 加锁通信超时";
                }
            }
        }
        return redisConnection ? "Redis 加锁连接异常" : "锁组件异常，请核对服务端日志";
    }

    /**
     * 按本次调用策略恢复历史回执或执行适配器，并更新本次调用日志。
     *
     * @param <T> 接口的业务回执类型
     * @param actionId 写入本次调用日志的流程动作执行编号
     * @param journal 本笔推送的幂等记录与调用日志访问对象
     * @param restoreResult 从已保存的文本还原业务回执的函数
     * @param sendRequest 执行实际推送并返回调用结果的函数
     * @return 复用或实际推送取得的业务回执
     * @throws JournalException 适配器调用失败、结果未知，或成功后的 OA 记录写入未获确认
     */
    private <T> T executeAttempt(String actionId, JournalService journal, Function<String, T> restoreResult,
                                Supplier<JournalResult<T>> sendRequest) {
        String cachedResult = journal.beginAttempt(actionId);
        if (cachedResult != null) {
            return Objects.requireNonNull(restoreResult.apply(cachedResult), "无法恢复已保存的业务结果");
        }
        JournalResult<T> result;
        try {
            result = Objects.requireNonNull(sendRequest.get(), "适配器未返回业务结果");
        } catch (JournalException failure) {
            recordFailure(journal, failure);
            throw failure;
        } catch (RuntimeException unexpected) {
            JournalException unknown = new JournalException(JournalErrors.ADAPTER, "调用异常，请先核对第三方结果",
                    true, null, null, null);
            unknown.addSuppressed(unexpected);
            recordFailure(journal, unknown);
            throw unknown;
        }
        try {
            journal.recordSuccess(result);
        } catch (RuntimeException failure) {
            JournalException pending = new JournalException(JournalErrors.VERIFY,
                    "第三方已返回成功，OA 记录写入未确认，请核对日志 " + journal.getCallLogId(),
                    true, null, result.httpStatus, result.businessCode);
            pending.addSuppressed(failure);
            recordFailure(journal, pending);
            throw pending;
        }
        return result.data;
    }

    /**
     * 根据调用结果将本次日志更新为 FAILED 或 UNKNOWN。若日志写入失败，
     * 将写入异常附加到原调用异常，供上层一并追溯。
     *
     * @param journal 本笔推送的调用日志访问对象
     * @param outcome 即将向上层传播的调用异常
     */
    private void recordFailure(JournalService journal, JournalException outcome) {
        try {
            journal.recordFailure(outcome);
        } catch (RuntimeException storageFailure) {
            outcome.addSuppressed(storageFailure);
        }
    }
}
