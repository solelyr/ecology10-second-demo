package com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.response;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @USER: solelyr
 * @DATE: 2026/11/06 11:16
 * @DESCRIPTION: 承载已确认的第三方供应商新增成功结果
 */
@Data
@NoArgsConstructor
public class VendorCreated {
    /** 第三方实际返回的供应商编码，不假定等于请求编码。 */
    private String vendorCode;
}
