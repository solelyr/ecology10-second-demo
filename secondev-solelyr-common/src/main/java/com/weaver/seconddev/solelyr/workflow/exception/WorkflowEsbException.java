package com.weaver.seconddev.solelyr.workflow.exception;

import java.util.Objects;

/**
 * @USER: solelyr
 * @DATE: 2026/11/11 15:10
 * @DESCRIPTION: 携带模块错误码、业务说明和原始原因
 */
public class WorkflowEsbException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_MESSAGE = "工作流 ESB 执行失败，请核对服务端日志";

    private final String errorCode;

    public WorkflowEsbException(WorkflowErrorCode code, String message) {
        this(code, message, null);
    }

    public WorkflowEsbException(WorkflowErrorCode code, String message, Throwable cause) {
        this(Objects.requireNonNull(code, "code").code(), message, cause);
    }

    private WorkflowEsbException(String errorCode, String message, Throwable cause) {
        super(message == null || message.trim().isEmpty() ? DEFAULT_MESSAGE : message, cause);
        if (errorCode == null || errorCode.trim().isEmpty()) {
            throw new IllegalArgumentException("工作流错误码不能为空");
        }
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    /** 补充调用位置的业务信息，保留原错误码和异常链。 */
    public WorkflowEsbException withContext(String message) {
        return new WorkflowEsbException(errorCode, message, this);
    }

    @Override
    public String toString() {
        return super.toString() + " [errorCode=" + errorCode + "]";
    }
}
