package com.weaver.seconddev.solelyr.workflow.exception;


/**
 * @USER: solelyr
 * @DATE: 2026/11/11 15:10
 * @DESCRIPTION: 工作流 ESB 入口的统一异常，适用于表单查询和自定义策略执行
 */
public class WorkflowEsbException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** 异常类别。 */
    public enum Kind {
        /** 调用参数或用户上下文无效。 */
        VALIDATION,
        /** 执行所需的业务数据不存在。 */
        NOT_FOUND,
        /** 字段定义缺少数据库列名、表名或存在重复列。 */
        METADATA,
        /** 自定义策略或其调用的接口执行失败。 */
        EXECUTION
    }

    private final Kind kind;

    /**
     * 创建 ESB 异常。
     *
     * @param kind 异常类别
     * @param message 可直接返回给调用方的说明，不含业务数据
     */
    public WorkflowEsbException(Kind kind, String message) {
        this(kind, message, null);
    }

    /** 创建自定义策略执行异常，message 用于响应，cause 用于服务端日志。 */
    public WorkflowEsbException(String message, Throwable cause) {
        this(Kind.EXECUTION, message, cause);
    }

    public WorkflowEsbException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    /**
     * 获取异常类别。
     *
     * @return 异常类别
     */
    public Kind getKind() {
        return kind;
    }
}
