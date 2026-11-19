package com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.response;

import java.util.List;
import java.math.BigDecimal;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @USER: solelyr
 * @DATE: 2026/11/06 11:16
 * @DESCRIPTION: 封装获取 Token 的业务状态和令牌响应
 */
@Data
@NoArgsConstructor
public class TokenResponse {
    /** 业务状态码，成功为 1。 */
    private BigDecimal code;
    /** 业务成功标识，文档为字符串 true。 */
    private String success;
    /** 成功时携带 Token，不记录到日志。 */
    private String message;
    /** 文档成功示例为空数组。 */
    private List<String> data;
}
