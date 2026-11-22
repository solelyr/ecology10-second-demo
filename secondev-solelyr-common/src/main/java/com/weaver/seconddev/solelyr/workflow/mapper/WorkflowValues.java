package com.weaver.seconddev.solelyr.workflow.mapper;

import com.weaver.seconddev.solelyr.workflow.exception.WorkflowErrors;
import com.weaver.seconddev.solelyr.workflow.exception.WorkflowEsbException;
import com.weaver.seconddev.solelyr.workflow.model.WorkflowEsbForm;
import com.weaver.seconddev.solelyr.workflow.model.WorkflowFieldInfo;

import java.util.Map;

/**
 * @USER: solelyr
 * @DATE: 2026/11/13 10:47
 * @DESCRIPTION: 按 OA 字段元数据读取已核验的主表及明细值
 */
public final class WorkflowValues {
    /**
     * 创建表单字段读取工具。
     */
    private WorkflowValues() {
    }

    /**
     * 读取 OA 主表字段。
     *
     * @param form 流程表单
     * @param key 字段 dataKey
     * @return 原始字段值
     */
    public static String main(WorkflowEsbForm form, String key) {
        return value(form, form.getMainData(), key, null);
    }

    /**
     * 按数据键和明细表定位字段。
     *
     * @param form 流程表单
     * @param row 当前表数据
     * @param key 字段 dataKey
     * @param table 明细表名，主表传 null
     * @return 原始字段值
     */
    public static String value(WorkflowEsbForm form, Map<String, Object> row, String key, String table) {
        String column = null;
        for (WorkflowFieldInfo field : form.getFields()) {
            if (key.equals(field.getDataKey()) && field.isDetail() == (table != null)
                    && (table == null || table.equals(field.getTableName())) && field.getColumnName() != null) {
                if (column != null) {
                    throw new WorkflowEsbException(WorkflowErrors.FIELD_METADATA_INVALID,
                            "OA 表单字段重复 " + key);
                }
                column = field.getColumnName();
            }
        }
        Object raw = column == null ? null : row.get(column);
        return raw == null ? null : raw.toString();
    }
}
