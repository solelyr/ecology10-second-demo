package com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.response;

import java.util.List;
import java.math.BigDecimal;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @USER: solelyr
 * @DATE: 2026/11/06 11:16
 * @DESCRIPTION: 封装第三方供应商新增响应和待核验的返回编码列表
 */
@Data
@NoArgsConstructor
public class AddVendorResponse {
    /** 业务状态码，成功为 1。 */
    private BigDecimal code;
    /** 业务成功标识，兼容文档字符串及布尔 true。 */
    private String success;
    /** 第三方说明，不直接作为面向用户的异常。 */
    private String message;
    /** 新增结果列表；协议校验后取唯一字符串供应商编码，保留原始元素类型以拒绝数值账号。 */
    private List<?> data;
}
