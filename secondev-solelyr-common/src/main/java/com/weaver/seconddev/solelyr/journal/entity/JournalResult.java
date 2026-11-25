package com.weaver.seconddev.solelyr.journal.entity;

import java.util.Objects;

/**
 * @USER: solelyr
 * @DATE: 2026/11/22 21:57
 * @DESCRIPTION: 封装适配器确认成功后可持久化和复用的业务结果
 */
public final class JournalResult<T> {
    /** 当前接口已确认成功、可保存并复用的业务返回对象。 */
    public final T data;
    /** 本次调用收到的 HTTP 状态码。 */
    public final Integer httpStatus;
    /** 第三方返回的业务状态码，例如 U8 成功码 1。 */
    public final String businessCode;

    public JournalResult(T data, Integer httpStatus, String businessCode) {
        this.data = Objects.requireNonNull(data, "data");
        this.httpStatus = httpStatus;
        this.businessCode = businessCode;
    }
}
