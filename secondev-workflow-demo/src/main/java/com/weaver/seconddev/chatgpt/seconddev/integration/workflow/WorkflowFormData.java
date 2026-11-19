package com.weaver.seconddev.chatgpt.seconddev.integration.workflow;

import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @USER: solelyr
 * @DATE: 2026/11/10 20:38
 * @DESCRIPTION: 保存流程关联表单的标识及以 dataKey 为键的主明细数据
 */
@Data
@NoArgsConstructor
public class WorkflowFormData {
    /** 流程关联的 eBuilder 应用 ID。 */
    private String appId;
    /** 流程关联的 E10 内部表单 ID。 */
    private long formId;
    /** 当前流程关联的实际表单记录 ID。 */
    private long formDataId;
    /** 流程编号。 */
    private String requestMark;
    /** 主表字段的 dataKey 与 E10 返回的值。 */
    private Map<String, Object> mainData;
    /** 以明细表 dataKey 为键，行按 dataIndex 排序，每行以字段 dataKey 为键。 */
    private Map<String, List<Map<String, Object>>> detailData;
}
