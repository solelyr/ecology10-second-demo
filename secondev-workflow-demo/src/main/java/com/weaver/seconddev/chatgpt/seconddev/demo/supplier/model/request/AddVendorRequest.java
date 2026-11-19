package com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.request;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @USER: solelyr
 * @DATE: 2026/11/06 11:16
 * @DESCRIPTION: 封装第三方供应商新增请求及供应商档案
 */
@Data
@NoArgsConstructor
public class AddVendorRequest {
    /** 服务端取得的第三方 Token。 */
    @JSONField(name = "Token")
    private String token;
    /** 稳定业务单号；不等于已证明幂等。 */
    @JSONField(name = "billid")
    private String billId;
    /** 第三方账套编码。 */
    @JSONField(name = "AccId")
    private String accId;
    /** 业务日期，可空；非空为 yyyy-MM-dd。 */
    @JSONField(name = "LoginDate")
    private String loginDate;
    /** 供应商档案实体。 */
    @JSONField(name = "oArchives")
    private VendorArchive archives;
}
