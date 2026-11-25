package com.weaver.seconddev.solelyr.workflow.util;

/**
 * @USER: solelyr
 * @DATE: 2026/11/13 22:27
 * @DESCRIPTION: 定义 E10 数据源分组的名称、服务标识和分组 ID
 * 通过 /api/datasource/ds/group?sourceType=LOGIC 接口获取数据源标识 EXTERNAL 外部
 */
public interface SecondevDataSourceGroup {
    /** eBuilder 应用元数据所在的 E10 逻辑数据源分组。 */
    String EBUILDER_APP_GROUP_ID = "weaver-ebuilder-app-service";

    /** 数据源显示名称。 */
    String getDisplayName();
    /** E10 业务服务标识，部分数据源没有该字段。 */
    String getServiceMark();
    /** 调用 {@code SecondevDataSetUtil} 时使用的数据源 groupId。 */
    String getGroupId();
}
