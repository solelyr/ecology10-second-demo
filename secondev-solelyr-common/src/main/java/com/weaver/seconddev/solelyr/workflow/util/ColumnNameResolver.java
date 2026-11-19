package com.weaver.seconddev.solelyr.workflow.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import com.weaver.common.form.metadata.field.FormField;
import com.weaver.common.form.physical.entity.FormTableField;
import com.weaver.seconddev.solelyr.workflow.exception.WorkflowEsbException;
import java.util.regex.Pattern;

/**
 * @USER: solelyr
 * @DATE: 2026/11/11 15:10
 * @DESCRIPTION: 解析表单字段对应的数据库列名：优先物理列 form_table_field.column_name，
 *               其次 FormField#getColumnName，最后 otherProperties.columnName
 */
public final class ColumnNameResolver {
    /** 与 form_table_field.column_name 的长度 64 一致，只允许常规标识符。 */
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

    /**
     * 工具类，禁止实例化。
     */
    private ColumnNameResolver() {
    }

    /**
     * 解析字段的数据库列名。
     *
     * @param field 平台返回的字段定义
     * @return 数据库列名；字段没有列（如纯布局组件）时返回 null
     */
    public static String resolve(FormField field) {
        return resolve(field, null);
    }

    /**
     * 解析字段的数据库列名，优先使用物理表字段信息。
     *
     * @param field 平台返回的字段定义
     * @param physical 物理表字段信息（form_table_field），可为 null
     * @return 数据库列名；字段没有列（如纯布局组件）时返回 null
     */
    public static String resolve(FormField field, FormTableField physical) {
        if (field == null) {
            return null;
        }
        String physicalName = physical == null ? null : trimToNull(physical.getColumnName());
        if (physicalName != null) {
            return checked(physicalName, field);
        }
        String direct = trimToNull(field.getColumnName());
        if (direct != null) {
            return checked(direct, field);
        }
        String fromProperties = fromOtherProperties(field.getOtherProperties());
        return fromProperties == null ? null : checked(fromProperties, field);
    }

    /**
     * 判断名称是否为安全的数据库标识符。
     *
     * @param name 表名或列名
     * @return 是否只包含字母、数字和下划线且不以数字开头
     */
    public static boolean isSafeIdentifier(String name) {
        return name != null && SAFE_IDENTIFIER.matcher(name).matches();
    }

    /**
     * 从字段 otherProperties JSON 中读取 columnName。JSON 无效时视为未提供。
     *
     * @param otherProperties 字段扩展属性 JSON
     * @return 去除首尾空白的列名，或 null
     */
    private static String fromOtherProperties(String otherProperties) {
        if (trimToNull(otherProperties) == null) {
            return null;
        }
        try {
            Object parsed = JSON.parse(otherProperties);
            if (!(parsed instanceof JSONObject)) {
                return null;
            }
            Object value = ((JSONObject) parsed).get("columnName");
            return value instanceof String ? trimToNull((String) value) : null;
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * 校验列名格式，防止把异常元数据当作结果键或后续 SQL 标识符。
     *
     * @param name 待校验列名
     * @param field 列名所属字段
     * @return 原列名
     */
    private static String checked(String name, FormField field) {
        if (!isSafeIdentifier(name)) {
            throw new WorkflowEsbException(WorkflowEsbException.Kind.METADATA,
                    "字段 " + field.getId() + " 返回的数据库列名格式无效");
        }
        return name;
    }

    /**
     * 去除首尾空白，空字符串转为 null。
     *
     * @param value 原始字符串
     * @return 处理后的字符串
     */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
