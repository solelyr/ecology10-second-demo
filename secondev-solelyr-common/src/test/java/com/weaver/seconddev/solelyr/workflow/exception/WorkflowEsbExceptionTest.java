package com.weaver.seconddev.solelyr.workflow.exception;

import static com.weaver.seconddev.solelyr.workflow.TestSupport.check;
import static com.weaver.seconddev.solelyr.workflow.TestSupport.eq;

import com.weaver.seconddev.solelyr.workflow.TestSupport;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

/**
 * @USER: solelyr
 * @DATE: 2026/11/21 23:02
 * @DESCRIPTION: 验证统一异常处理器的定位、日志次数和失败响应
 */
public final class WorkflowEsbExceptionTest {
    /**
     * 测试入口类，禁止实例化。
     */
    private WorkflowEsbExceptionTest() {
    }

    /**
     * 捕获真实 SLF4J/Log4j2 日志，离线验证入口类型、日志位置及异常链。
     *
     * @param args 未使用
     */
    public static void main(String[] args) {
        // 使用同步日志，使每次入口调用后的事件数量可直接断言。
        System.setProperty("Log4jContextSelector", "org.apache.logging.log4j.core.selector.BasicContextSelector");
        System.setProperty("log4j2.contextSelector", "org.apache.logging.log4j.core.selector.BasicContextSelector");
        List<LogEvent> events = new ArrayList<>();
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();
        String loggerName = WorkflowEsbExceptionHandler.class.getName();
        LoggerConfig previous = configuration.getLoggers().get(loggerName);
        AbstractAppender appender = new AbstractAppender("workflow-exception-test", null,
                PatternLayout.createDefaultLayout(), false) {
            /**
             * 保存真实日志事件，供用例断言级别、文本及完整异常对象。
             *
             * @param event 日志事件
             */
            @Override
            public void append(LogEvent event) {
                events.add(event.toImmutable());
            }
        };
        appender.start();
        LoggerConfig loggerConfig = new LoggerConfig(loggerName, Level.ERROR, false);
        loggerConfig.addAppender(appender, Level.ERROR, null);
        configuration.addLogger(loggerName, loggerConfig);
        context.updateLoggers();
        WorkflowEsbExceptionHandler handler = new WorkflowEsbExceptionHandler();
        try {
            check("下级自定义码保留在异常、包装和日志中", () -> {
                events.clear();
                WorkflowErrorCode code = () -> "EXAMPLE.INVALID_FIELD";
                WorkflowEsbException original = new WorkflowEsbException(code, "字段无效");
                WorkflowEsbException wrapped = original.withContext("第一行字段无效");
                eq("EXAMPLE.INVALID_FIELD", wrapped.getErrorCode());
                eq(original, wrapped.getCause());
                eq("第一行字段无效", handler.handle(wrapped).getMsg());
                eq(1, events.size());
                contains(events.get(0), "errorCode=EXAMPLE.INVALID_FIELD");
            });
            check("直接抛出时由入口记录，两个位置都指向业务代码", () -> {
                events.clear();
                try {
                    throwDirectly();
                    throw new AssertionError("应抛出业务异常");
                } catch (WorkflowEsbException failure) {
                    eq(0, events.size());
                    eq(failure.getMessage(), handler.handle(failure).getMsg());
                    eq(1, events.size());
                    LogEvent event = events.get(0);
                    eq(Level.ERROR, event.getLevel());
                    eq(failure, event.getThrown());
                    contains(event, "errorCode=EXAMPLE.LOCK_FAILED");
                    contains(event, "callSite=" + failure.getStackTrace()[0]);
                    contains(event, "origin=" + failure.getStackTrace()[0]);
                    contains(event, "WorkflowEsbExceptionTest.throwDirectly(");
                }
            });
            check("多层包装和回写失败在入口合并为一条完整日志", () -> {
                events.clear();
                IllegalStateException root = new IllegalStateException("fixture root");
                WorkflowEsbException inner = new WorkflowEsbException(() -> "EXAMPLE.CONNECTION_FAILED", "连接失败", root);
                WorkflowEsbException outer = new WorkflowEsbException(WorkflowErrors.FIELD_METADATA_INVALID, "配置读取失败", inner);
                RuntimeException writeFailure = new RuntimeException("fixture writeback failure");
                outer.addSuppressed(writeFailure);
                eq(0, events.size());
                eq("配置读取失败", handler.handle(outer).getMsg());
                eq(1, events.size());
                LogEvent event = events.get(0);
                contains(event, "errorCode=WORKFLOW.FIELD_METADATA_INVALID");
                contains(event, "callSite=" + outer.getStackTrace()[0]);
                contains(event, "rootCause=" + root.getClass().getName());
                contains(event, "origin=" + root.getStackTrace()[0]);
                eq(outer, event.getThrown());
                eq(root, event.getThrown().getCause().getCause());
                eq(writeFailure, event.getThrown().getSuppressed()[0]);
            });
            check("Spring 只为工作流业务异常匹配处理方法", () -> {
                ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(WorkflowEsbExceptionHandler.class);
                eq(null, resolver.resolveMethod(new IllegalStateException("普通异常")));
                eq("handle", resolver.resolveMethod(new WorkflowEsbException(() -> "EXAMPLE.REQUEST_FAILED", "请求失败")).getName());
            });
            check("工作流异常缺少堆栈时位置为 unknown", () -> {
                events.clear();
                WorkflowEsbException failure = new WorkflowEsbException(() -> "EXAMPLE.REQUEST_FAILED", "请求失败");
                failure.setStackTrace(new StackTraceElement[0]);
                eq("请求失败", handler.handle(failure).getMsg());
                eq(1, events.size());
                contains(events.get(0), "callSite=unknown");
                contains(events.get(0), "origin=unknown");
            });
            check("底层 cause 成环时可以记录", () -> {
                events.clear();
                RuntimeException first = new RuntimeException("fixture first");
                RuntimeException second = new RuntimeException("fixture second", first);
                first.initCause(second);
                handler.handle(new WorkflowEsbException(() -> "EXAMPLE.REQUEST_FAILED", "失败", first));
                eq(1, events.size());
            });
        } finally {
            configuration.removeLogger(loggerName);
            if (previous != null) {
                configuration.addLogger(loggerName, previous);
            }
            context.updateLoggers();
            appender.stop();
        }
        TestSupport.finish("WorkflowEsbExceptionTest");
    }

    /**
     * 模拟直接抛出异常的业务调用点。
     */
    private static void throwDirectly() {
        throw new WorkflowEsbException(() -> "EXAMPLE.LOCK_FAILED", "无法取得接口幂等锁");
    }

    /**
     * 断言实际生成的日志文本包含指定诊断信息。
     *
     * @param event 实际日志事件
     * @param expected 必须出现的文本
     */
    private static void contains(LogEvent event, String expected) {
        String message = event.getMessage().getFormattedMessage();
        if (!message.contains(expected)) {
            throw new AssertionError("日志缺少 " + expected + ": " + message);
        }
    }
}
