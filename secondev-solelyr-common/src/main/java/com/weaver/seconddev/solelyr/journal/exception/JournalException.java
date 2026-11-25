package com.weaver.seconddev.solelyr.journal.exception;

import com.weaver.seconddev.solelyr.workflow.exception.WorkflowErrorCode;
import com.weaver.seconddev.solelyr.workflow.exception.WorkflowEsbException;

/**
 * @USER: solelyr
 * @DATE: 2026/11/22 21:57
 * @DESCRIPTION: 携带可对外展示的失败说明以及独立的发送状态和日志分类
 */
public final class JournalException extends WorkflowEsbException {
    /** 是否无法确认业务已生效，为 true 时调用日志记录 UNKNOWN。 */
    public final boolean uncertain;
    /** OA 日志的错误类型名称，如 TIMEOUT、HTTP_ERROR 或 BUSINESS_ERROR。 */
    public final String errorType;
    /** 已收到的 HTTP 状态码，尚未收到响应时为空。 */
    public final Integer httpStatus;
    /** 已解析到的第三方业务状态码。 */
    public final String businessCode;

    public JournalException(WorkflowErrorCode code, String message, boolean uncertain, String errorType,
                            Integer httpStatus, String businessCode) {
        super(code, message);
        this.uncertain = uncertain;
        this.errorType = errorType;
        this.httpStatus = httpStatus;
        this.businessCode = businessCode;
    }
}
