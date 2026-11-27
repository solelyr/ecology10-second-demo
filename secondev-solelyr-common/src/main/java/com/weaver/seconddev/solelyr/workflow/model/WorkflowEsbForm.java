package com.weaver.seconddev.solelyr.workflow.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @USER: solelyr
 * @DATE: 2026/11/11 15:10
 * @DESCRIPTION: 按 requestId 查询的流程表单结果：流程上下文、字段信息，以及以数据库列名为键的主表和明细数据
 */
@Data
@NoArgsConstructor
public class WorkflowEsbForm implements Serializable {
    /** 主表/明细行中用于存放记录 ID 的键，与物理表主键列同名。 */
    public static final String ID_KEY = "id";

    /** 流程实例 ID（requestId）。 */
    private long requestId;
    /** 执行本次查询的租户标识。 */
    private String tenantKey;
    /** 执行本次查询的操作用户 ID。 */
    private Long employeeId;
    /** 本次流程动作是否使用幂等管理，默认启用。 */
    private boolean manageIdempotency = true;
    /** 查询服务组装的动作入口参数。 */
    private Map<String, Object> params = Collections.emptyMap();
    /** 流程实例所属的工作流 ID。 */
    private long workflowId;
    /** 流程标题。 */
    private String requestName;
    /** 流程编号（requestMark）。 */
    private String requestMark;
    /** 流程关联的表单 ID（relateKey）。 */
    private long formId;
    /** 流程关联的表单数据记录 ID。 */
    private long dataId;
    /** 主表数据库表名；平台未返回时为 null。 */
    private String mainTableName;
    /** 流程关联表单的字段信息，顺序与平台返回的字段定义一致。 */
    private List<WorkflowFieldInfo> fields = new ArrayList<>();
    /** 主表数据：数据库列名 → 值。附件的“列名_url”保存下载地址，多文件按附件顺序以英文逗号拼接。 */
    private Map<String, Object> mainData = new LinkedHashMap<>();
    /** 明细数据：明细表数据库表名 → 行列表，每行为 数据库列名 → 值，附件下载地址使用同一行的“列名_url”。 */
    private Map<String, List<Map<String, Object>>> detailData = new LinkedHashMap<>();
    /** 主表显示内容：数据库列名 → 显示文本（选项名称、文件名、人员姓名等，多值以英文逗号拼接）。 */
    private Map<String, Object> mainText = new LinkedHashMap<>();
    /** 明细显示内容：结构同 detailData，值为显示文本。 */
    private Map<String, List<Map<String, Object>>> detailText = new LinkedHashMap<>();

    /**
     * 按数据库列名读取主表值。
     *
     * @param columnName 数据库列名
     * @return 字段值；不存在时为 null
     */
    public Object getMainValue(String columnName) {
        return mainData.get(columnName);
    }

    /**
     * 按明细表数据库表名读取行，不存在时返回空列表。
     *
     * @param tableName 明细表数据库表名
     * @return 只读行列表
     */
    public List<Map<String, Object>> getDetailRows(String tableName) {
        List<Map<String, Object>> rows = detailData.get(tableName);
        return rows == null ? Collections.<Map<String, Object>>emptyList() : Collections.unmodifiableList(rows);
    }

    /**
     * 按数据库列名读取主表显示内容。
     *
     * @param columnName 数据库列名
     * @return 显示文本；不存在时为 null
     */
    public Object getMainText(String columnName) {
        return mainText.get(columnName);
    }

    /**
     * 按明细表数据库表名读取显示内容行，不存在时返回空列表。
     *
     * @param tableName 明细表数据库表名
     * @return 只读行列表
     */
    public List<Map<String, Object>> getDetailTextRows(String tableName) {
        List<Map<String, Object>> rows = detailText.get(tableName);
        return rows == null ? Collections.<Map<String, Object>>emptyList() : Collections.unmodifiableList(rows);
    }

    /**
     * 转为便于 Action、接口或 JSON 输出的 Map 结构。
     *
     * @return 包含流程上下文、字段信息、主表与明细数据的 Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("requestId", String.valueOf(requestId));
        map.put("manageIdempotency", manageIdempotency);
        map.put("tenantKey", tenantKey);
        map.put("employeeId", employeeId == null ? null : employeeId.toString());
        map.put("workflowId", String.valueOf(workflowId));
        map.put("requestName", requestName);
        map.put("requestMark", requestMark);
        map.put("formId", String.valueOf(formId));
        map.put("dataId", String.valueOf(dataId));
        map.put("mainTableName", mainTableName);
        map.put("fields", fields);
        map.put("mainData", mainData);
        map.put("detailData", detailData);
        map.put("mainText", mainText);
        map.put("detailText", detailText);
        return map;
    }
}
