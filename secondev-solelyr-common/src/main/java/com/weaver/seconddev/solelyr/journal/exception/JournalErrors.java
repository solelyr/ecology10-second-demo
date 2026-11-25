package com.weaver.seconddev.solelyr.journal.exception;

import com.weaver.seconddev.solelyr.workflow.exception.WorkflowErrorCode;

/**
 * @USER: solelyr
 * @DATE: 2026/11/22 21:57
 * @DESCRIPTION: 集成执行、存储与结果核对的稳定业务错误码
 */
public enum JournalErrors implements WorkflowErrorCode {
    BUSY("PUSH.IN_PROGRESS"), VERIFY("PUSH.VERIFY_REQUIRED"),
    STORAGE("PUSH.STORAGE_FAILED"), ADAPTER("PUSH.ADAPTER_FAILED");

    /** 枚举对应的显式稳定业务错误码。 */
    private final String value;

    JournalErrors(String value) {
        this.value = value;
    }

    public String code() {
        return value;
    }
}
