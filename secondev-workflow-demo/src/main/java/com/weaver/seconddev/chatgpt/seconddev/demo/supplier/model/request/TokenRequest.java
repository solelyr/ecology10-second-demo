package com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.request;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @USER: solelyr
 * @DATE: 2026/11/06 11:16
 * @DESCRIPTION: 封装第三方获取 Token 的应用标识和密钥请求
 */
@Data
@NoArgsConstructor
public class TokenRequest {
    /** 第三方应用标识。 */
    private String appid;
    /** 第三方应用密钥，仅从服务端配置读取。 */
    private String secretkey;
}
