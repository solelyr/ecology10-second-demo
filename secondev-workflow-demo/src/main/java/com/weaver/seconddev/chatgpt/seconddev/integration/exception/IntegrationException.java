package com.weaver.seconddev.chatgpt.seconddev.integration.exception;

/**
 * @USER: solelyr
 * @DATE: 2026/11/06 09:53
 * @DESCRIPTION: 统一描述校验、接口调用和回写失败等业务异常
 */
public class IntegrationException extends RuntimeException {
    public enum Kind {
        /** 输入字段或其他必填值未通过校验。 */
        VALIDATION,

        /** HTTP 请求未完成，或收到非成功状态码。 */
        TRANSPORT,

        /** 第三方响应无法解析，或响应结构不符合接口约定。 */
        PROTOCOL,

        /** 第三方响应表示业务处理失败。 */
        REJECTED,

        /** 第三方操作成功后，E10 回写失败或未确认成功。 */
        WRITEBACK,

        /** 推送功能未启用，或服务配置缺失、无效。 */
        CONFIGURATION
    }

    private final Kind kind;

    /**
     * 创建带分类及对外提示的集成异常。
     *
     * @param kind 异常分类
     * @param safeMessage 可返回给调用方的错误提示
     */
    public IntegrationException(Kind kind, String safeMessage) {
        super(safeMessage);
        this.kind = kind;
    }

    /**
     * 创建保留原始原因的集成异常。
     *
     * @param kind 异常分类
     * @param message 错误提示
     * @param cause 原始异常
     */
    public IntegrationException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    /**
     * 获取异常分类。
     *
     * @return 当前集成异常所属类别
     */
    public Kind getKind() {
        return kind;
    }
}
