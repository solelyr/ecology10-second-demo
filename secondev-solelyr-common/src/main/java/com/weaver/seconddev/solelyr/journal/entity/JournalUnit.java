package com.weaver.seconddev.solelyr.journal.entity;

/**
 * @USER: solelyr
 * @DATE: 2026/11/22 21:57
 * @DESCRIPTION: 描述独立业务操作的上下文与调用方提供的幂等键
 */
public final class JournalUnit {
    /** 当前流程所属的 OA 租户标识。 */
    public final String tenant;
    /** 第三方目标系统名称，对应日志表和幂等状态表的 mbxt 选项。 */
    public final String system;
    /** 接口操作标识，供应商示例使用 U8 接口相对路径，写入 jkcz。 */
    public final String operation;
    /** 实际业务请求的完整接口地址，写入调用日志 jkdz。 */
    public final String requestUrl;
    /** 业务对象标识，用于日志上下文定位具体业务。 */
    public final String objectId;
    /** 调用方传入的明文幂等键，原样写入 mdj。 */
    public final String key;
    /** 业务调用方决定本次是否加同键锁并读写幂等状态表。 */
    public final boolean manageIdempotency;
    /** 触发本次调用的 OA 流程实例 ID。 */
    public final long requestId;
    /** 本次执行的 OA 操作人 ID，用于 E10 表单 RPC 的身份与权限校验。 */
    public final long employeeId;
    /** 写入调用日志 qqzw 的脱敏请求摘要，用于排查本次推送的业务内容。 */
    public final String requestSummary;
    /** 本次业务请求使用的 HTTP 方法，由调用方按实际发送方式明确提供，写入 httff。 */
    public final String httpMethod;

    /** key 为调用方定义的稳定明文键，写入 OA 的 mdj 字段，最多 100 字符。 */
    public JournalUnit(String tenant, Long employeeId, long requestId, String system, String operation,
                       String objectId, String key, String requestSummary, String httpMethod) {
        this(tenant, employeeId, requestId, system, operation, objectId, key, requestSummary, httpMethod, operation);
    }

    /** 分别提供稳定的接口操作标识和用于日志追溯的完整请求地址。 */
    public JournalUnit(String tenant, Long employeeId, long requestId, String system, String operation,
                       String objectId, String key, String requestSummary, String httpMethod, String requestUrl) {
        this(tenant, employeeId, requestId, system, operation, objectId, key, requestSummary, httpMethod, requestUrl, true);
    }

    /** 按本次调用的策略管理幂等；关闭时 key 可为空，调用日志仍逐次记录。 */
    public JournalUnit(String tenant, Long employeeId, long requestId, String system, String operation,
                       String objectId, String key, String requestSummary, String httpMethod, String requestUrl,
                       boolean manageIdempotency) {
        if (tenant == null || tenant.trim().isEmpty() || requestId <= 0 || employeeId == null || employeeId <= 0) {
            throw new IllegalArgumentException("缺少流程实例、租户或操作人标识");
        }
        if ((manageIdempotency && (key == null || key.trim().isEmpty())) || (key != null && key.length() > 100)) {
            throw new IllegalArgumentException("启用幂等管理时必须提供键，日志键最多 100 字符");
        }
        if (httpMethod == null || !httpMethod.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) {
            throw new IllegalArgumentException("调用方必须提供有效的 HTTP 方法");
        }
        this.employeeId = employeeId;
        this.tenant = tenant;
        this.requestId = requestId;
        this.system = system;
        this.operation = operation;
        this.requestUrl = requestUrl;
        this.objectId = objectId;
        this.requestSummary = requestSummary;
        this.manageIdempotency = manageIdempotency;
        this.key = key;
        this.httpMethod = httpMethod;
    }

}
