package com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.request;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @USER: solelyr
 * @DATE: 2026/11/06 11:16
 * @DESCRIPTION: 封装供应商档案并保持第三方接口原始 JSON 字段名
 */
@Data
@NoArgsConstructor
public class VendorArchive {
    /** 供应商编码，保留前导零。 */
    @JSONField(name = "cVenCode")
    private String vendorCode;
    /** 供应商名称。 */
    @JSONField(name = "cVenName")
    private String vendorName;
    /** 供应商简称。 */
    @JSONField(name = "cVenAbbName")
    private String vendorAbbreviation;
    /** 第三方分类编码，不是分类名称。 */
    @JSONField(name = "cVCCode")
    private String categoryCode;
    /** 币种名称。 */
    @JSONField(name = "cVenExch_name")
    private String currencyName;
    /** 发展日期，格式 yyyy-MM-dd；当前表单没有来源。 */
    @JSONField(name = "dVenDevDate")
    private String developmentDate;
    /** 供应商税号。 */
    @JSONField(name = "cVenRegCode")
    private String taxNumber;
    /** 开户银行名称。 */
    @JSONField(name = "cVenBank")
    private String bankName;
    /** 银行账号，必须是字符串。 */
    @JSONField(name = "cVenAccount")
    private String bankAccount;
}
