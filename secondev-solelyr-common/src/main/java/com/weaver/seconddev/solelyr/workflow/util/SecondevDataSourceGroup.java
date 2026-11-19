package com.weaver.seconddev.solelyr.workflow.util;

/**
 * @USER: solelyr
 * @DATE: 2026/11/13 22:27
 * @DESCRIPTION: 定义 E10 数据源分组的名称、服务标识和分组 ID
 */
public interface SecondevDataSourceGroup {
    /** 数据源显示名称。 */
    String getDisplayName();
    /** E10 业务服务标识，部分数据源没有该字段。 */
    String getServiceMark();
    /** 调用 {@code SecondevDataSetUtil} 时使用的数据源 groupId。 */
    String getGroupId();
}
