package com.weaver.seconddev.solelyr.workflow.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @USER: solelyr
 * @DATE: 2026/11/11 15:10
 * @DESCRIPTION: 流程关联表单的单个字段信息，包含字段 ID、名称、dataKey、数据库列名及所属表
 */
@Data
@NoArgsConstructor
public class WorkflowFieldInfo {
    /** 字段 ID。 */
    private Long fieldId;
    /** 字段显示名称。 */
    private String title;
    /** 字段 dataKey。 */
    private String dataKey;
    /** 数据库列名；纯布局组件等无列字段为 null。 */
    private String columnName;
    /** 字段所在的数据库表名；平台未返回时为 null。 */
    private String tableName;
    /** 是否为明细表字段。 */
    private boolean detail;
    /** 明细表 ID；主表字段为 null。 */
    private Long subFormId;
    /** 明细表 dataKey；主表字段为 null。 */
    private String subFormDataKey;
    /** 组件类型标识。 */
    private String componentKey;
    /** 字段数据类型枚举名。 */
    private String dataType;
    /** 是否必填。 */
    private boolean required;
}
