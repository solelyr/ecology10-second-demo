package com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.action;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @USER: solelyr
 * @DATE: 2026/11/06 09:53
 * @DESCRIPTION: 承接从 E10 查询得到的表单上下文、主明细数据及第三方单据参数
 */
@Data
@NoArgsConstructor
public class SupplierSubmission {
    /** 内部表单配置 ID，不是管理页面 objId。 */
    private String formId;
    /** 当前表单记录 ID，不假定等于流程 ID。 */
    private String formDataId;
    /** 表单所属应用 ID。 */
    private String appId;
    /** 按 requestid 查得的流程编号，映射到第三方 billid。 */
    private String billId;
    /** 第三方账套编码。 */
    private String accId;
    /** 第三方业务日期，可空。 */
    private String loginDate;
    /** 查询并还原后的主表数据，键为字段的 dataKey。 */
    private Map<String, Object> mainData;
    /** 键为 E10 明细表的 dataKey，行按 dataIndex 排序，每行以字段 dataKey 为键。 */
    private Map<String, List<Map<String, Object>>> detailData = Collections.emptyMap();
}
