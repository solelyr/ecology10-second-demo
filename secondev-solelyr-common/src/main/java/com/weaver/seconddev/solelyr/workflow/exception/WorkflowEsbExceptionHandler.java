package com.weaver.seconddev.solelyr.workflow.exception;

import com.weaver.common.base.entity.result.WeaResult;
import com.weaver.common.base.enumeration.result.WeaResultCodeEnum;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @USER: solelyr
 * @DATE: 2026/11/21 23:39
 * @DESCRIPTION: 统一处理二开 HTTP 和工作流 Action 入口的 WorkflowEsbException，记录故障位置并生成失败响应
 */
@RestControllerAdvice(basePackages = "com.weaver.seconddev")
public class WorkflowEsbExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(WorkflowEsbExceptionHandler.class);

    @ExceptionHandler(WorkflowEsbException.class)
    public WeaResult<?> handle(WorkflowEsbException failure) {
        return failureResponse("Workflow ESB request failed", failure);
    }

    /** 已捕获异常的入口在完成回写和资源释放后调用一次。 */
    public static <T> WeaResult<T> failureResponse(String context, WorkflowEsbException failure) {
        logFailure(context, failure);
        return WeaResult.fail(WeaResultCodeEnum.ERROR.getCode(), failure.getMessage(), true);
    }

    /** RPC 入口向调用方返回业务异常说明，其他运行时异常使用入口指定的提示。 */
    public static <T> WeaResult<T> failureResponse(String context, RuntimeException failure, String fallbackMessage) {
        if (failure instanceof WorkflowEsbException) {
            return failureResponse(context, (WorkflowEsbException) failure);
        }
        LOG.error(context, failure);
        return WeaResult.fail(WeaResultCodeEnum.ERROR.getCode(), fallbackMessage, true);
    }

    /** 同时记录外层异常的创建位置和最深层原因的位置，保留完整异常链。 */
    private static void logFailure(String context, WorkflowEsbException failure) {
        Throwable root = failure;
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        visited.add(root);
        Throwable cause;
        while ((cause = root.getCause()) != null && visited.add(cause)) {
            root = cause;
        }
        LOG.error("{}; errorCode={}, callSite={}, rootCause={}, origin={}", context, failure.getErrorCode(),
                location(failure), root.getClass().getName(), location(root), failure);
    }

    private static String location(Throwable failure) {
        StackTraceElement[] trace = failure.getStackTrace();
        return trace.length == 0 ? "unknown" : trace[0].toString();
    }
}
