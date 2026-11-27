package com.weaver.seconddev.solelyr.workflow.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.weaver.common.form.metadata.ModuleSource;
import com.weaver.file.ud.api.FileDownloadService;
import com.weaver.seconddev.solelyr.workflow.exception.WorkflowErrors;
import com.weaver.common.form.data.FormData;
import com.weaver.common.form.data.detail.FileDetail;
import com.weaver.common.form.data.detail.FormDataDetail;
import com.weaver.common.form.data.option.FormDataOption;
import com.weaver.common.form.data.text.FormDataText;
import com.weaver.common.form.metadata.Form;
import com.weaver.common.form.metadata.field.FormField;
import com.weaver.common.form.metadata.subform.SubForm;
import com.weaver.common.form.physical.entity.FormTable;
import com.weaver.common.form.physical.entity.FormTableField;
import com.weaver.common.form.physical.service.FormTableFieldService;
import com.weaver.common.form.physical.service.FormTableService;
import com.weaver.common.hrm.util.HrmContextUtil;
import com.weaver.framework.rpc.annotation.RpcReference;
import com.weaver.seconddev.solelyr.workflow.exception.WorkflowEsbException;
import com.weaver.seconddev.solelyr.workflow.model.WorkflowFieldInfo;
import com.weaver.seconddev.solelyr.workflow.model.WorkflowEsbForm;
import com.weaver.seconddev.solelyr.workflow.util.ColumnNameResolver;
import com.weaver.teams.domain.user.SimpleEmployee;
import com.weaver.workflow.common.entity.formdef.RequestFormInfoEntity;
import com.weaver.workflow.common.entity.formdef.WfpRelateFormEntity;
import com.weaver.workflow.common.entity.rpc.WfcRequestRpcEntity;
import com.weaver.workflow.core.api.rest.publicapi.WfcFormFieldRest;
import com.weaver.workflow.core.api.rest.publicapi.WfcGetRequestDataRest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @USER: solelyr
 * @DATE: 2026/11/11 15:10
 * @DESCRIPTION: 通用流程表单查询：按 requestId 读取流程实例、表单字段定义和已保存数据，
 *               并把主表、明细数据转换为“数据库列名 → 值”的结构。数据库表名和列名以物理表元数据
 *               （form_table / form_table_field）为准，流程 RPC 返回的元数据仅作回退
 */
@Service("solelyrWorkflowFormQueryService")
public class WorkflowFormQueryService {
    @RpcReference
    private WfcGetRequestDataRest requestRest;

    @RpcReference
    private WfcFormFieldRest fieldRest;

    @Autowired
    private FormTableService formTableService;

    @Autowired
    private FormTableFieldService formTableFieldService;

    @Autowired
    private FileDownloadService fileDownloadService;

    /**
     * 供 Spring 创建实例，RPC 引用由平台注入。
     */
    public WorkflowFormQueryService() {
    }

    /**
     * 使用指定依赖创建实例，供离线测试或非 Spring 场景使用。
     *
     * @param requestRest 流程实例查询 RPC
     * @param fieldRest 流程表单字段和数据 RPC
     * @param formTableService 物理表信息服务
     * @param formTableFieldService 物理列信息服务
     */
    public WorkflowFormQueryService(WfcGetRequestDataRest requestRest, WfcFormFieldRest fieldRest,
                                    FormTableService formTableService, FormTableFieldService formTableFieldService) {
        this(requestRest, fieldRest, formTableService, formTableFieldService, null);
    }

    /** 使用指定的平台依赖构造查询服务，包括生成附件下载地址的文件服务。 */
    public WorkflowFormQueryService(WfcGetRequestDataRest requestRest, WfcFormFieldRest fieldRest,
                                    FormTableService formTableService, FormTableFieldService formTableFieldService,
                                    FileDownloadService fileDownloadService) {
        this.fileDownloadService = fileDownloadService;
        this.requestRest = requestRest;
        this.fieldRest = fieldRest;
        this.formTableService = formTableService;
        this.formTableFieldService = formTableFieldService;
    }

    /**
     * 按流程实例 ID 和操作用户查询表单数据。
     *
     * @param requestId 流程实例 ID
     * @param employeeId 当前操作用户 ID
     * @param tenantKey 当前租户标识
     * @return 流程上下文、字段信息及以数据库列名为键的数据
     */
    public WorkflowEsbForm query(long requestId, Long employeeId, String tenantKey) {
        if (employeeId == null || employeeId <= 0 || ColumnNameResolver.trimToNull(tenantKey) == null) {
            throw new WorkflowEsbException(WorkflowErrors.USER_CONTEXT_INVALID, "缺少有效的操作用户或租户");
        }
        return query(requestId, HrmContextUtil.build(employeeId, tenantKey.trim()));
    }

    /** 查询表单，并将本次 ESB 参数及其幂等策略一起交给业务处理器。 */
    public WorkflowEsbForm query(long requestId, Long employeeId, String tenantKey, Map<String, Object> params) {
        WorkflowEsbForm form = query(requestId, employeeId, tenantKey);

        Map<String, Object> snapshot = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
        form.setParams(snapshot);
        Object value = snapshot.get("idempotent");
        if (value instanceof BigDecimal) {
            form.setManageIdempotency(((BigDecimal) value).signum() != 0);
        } else if (value instanceof Number) {
            form.setManageIdempotency(((Number) value).doubleValue() != 0);
        } else {
            form.setManageIdempotency(!(value instanceof String && "0".equals(((String) value).trim())));
        }
        return form;
    }

    /**
     * 按流程实例 ID 和用户上下文查询表单数据。
     *
     * @param requestId 流程实例 ID
     * @param employee 当前用户和租户上下文
     * @return 流程上下文、字段信息及以数据库列名为键的数据
     */
    public WorkflowEsbForm query(long requestId, SimpleEmployee employee) {
        validate(requestId, employee);
        WfcRequestRpcEntity request = requestRest.getRequestEntity(requestId, employee);
        if (request == null || request.getRequestId() != requestId || request.getWorkflowId() <= 0) {
            throw new WorkflowEsbException(WorkflowErrors.REQUEST_NOT_FOUND, "未找到流程实例：" + requestId);
        }
        RequestFormInfoEntity relation = relatedForm(request.getFormInfos());
        List<FormField> fields = fieldRest.findFieldByWorkflowId(request.getWorkflowId(), employee);
        if (fields == null || fields.isEmpty()) {
            throw new WorkflowEsbException(WorkflowErrors.FIELD_METADATA_INVALID, "未查询到流程表单字段定义：" + request.getWorkflowId());
        }
        FormData formData = fieldRest.getFormDataByRequestId(Long.valueOf(requestId), employee);
        if (formData == null) {
            throw new WorkflowEsbException(WorkflowErrors.FORM_NOT_FOUND, "未查询到流程表单数据：" + requestId);
        }

        long formId = relation.getRelateFormObj().getRelateKey();
        Map<Long, FormField> fieldsById = new LinkedHashMap<>();
        for (FormField field : fields) {
            if (field != null && field.getId() != null) {
                fieldsById.put(field.getId(), field);
            }
        }
        Metadata meta = new Metadata(physicalTables(formId, employee),
                physicalFields(new ArrayList<>(fieldsById.keySet()), employee));

        WorkflowEsbForm result = new WorkflowEsbForm();
        result.setRequestId(requestId);
        result.setTenantKey(employee.getTenantKey());
        result.setEmployeeId(employee.getEmployeeId());
        result.setWorkflowId(request.getWorkflowId());
        result.setRequestName(request.getRequestName());
        result.setRequestMark(request.getRequestMark());
        result.setFormId(formId);
        result.setDataId(relation.getDataId());
        result.setMainTableName(mainTableName(formId, meta, formData, fields));
        for (FormField field : fieldsById.values()) {
            result.getFields().add(toInfo(field, result.getMainTableName(), meta));
        }
        convertData(result, fieldsById, formData, meta);
        return result;
    }

    /**
     * 校验调用参数。
     *
     * @param requestId 流程实例 ID
     * @param employee 用户上下文
     */
    private void validate(long requestId, SimpleEmployee employee) {
        if (requestId <= 0) {
            throw new WorkflowEsbException(WorkflowErrors.REQUEST_ID_INVALID, "requestId 必须是正整数");
        }
        if (employee == null || employee.getEmployeeId() == null || employee.getEmployeeId() <= 0
                || ColumnNameResolver.trimToNull(employee.getTenantKey()) == null) {
            throw new WorkflowEsbException(WorkflowErrors.USER_CONTEXT_INVALID, "缺少有效的操作用户或租户");
        }
        if (requestRest == null || fieldRest == null || formTableService == null || formTableFieldService == null) {
            throw new WorkflowEsbException(WorkflowErrors.PLATFORM_UNAVAILABLE, "平台依赖未注入，请通过 Spring 获取本组件");
        }
    }

    /**
     * 校验流程只关联一张有效表单。
     *
     * @param forms 流程关联表单
     * @return 关联信息
     */
    private RequestFormInfoEntity relatedForm(List<RequestFormInfoEntity> forms) {
        if (forms == null || forms.size() != 1 || forms.get(0) == null) {
            throw new WorkflowEsbException(WorkflowErrors.FORM_NOT_FOUND, "流程必须且只能关联一张表单");
        }
        RequestFormInfoEntity relation = forms.get(0);
        WfpRelateFormEntity form = relation.getRelateFormObj();
        if (form == null || form.getRelateKey() <= 0 || relation.getDataId() <= 0) {
            throw new WorkflowEsbException(WorkflowErrors.FORM_NOT_FOUND, "流程缺少有效的表单 ID 或表单数据 ID");
        }
        return relation;
    }

    /**
     * 查询主表单及其全部明细表的物理表名，键为 form_table.form_id（主表单 ID 或明细表 ID）。
     *
     * @param formId 流程关联的主表单 ID
     * @param employee 用户上下文
     * @return 表单/明细表 ID → 物理表名
     */
    private Map<Long, String> physicalTables(long formId, SimpleEmployee employee) {
        Map<Long, String> names = new LinkedHashMap<>();
        Map<Long, Boolean> active = new LinkedHashMap<>();
        List<FormTable> tables = formTableService.listFormTableByMainFormId(formId, employee);
        if (tables == null) {
            return names;
        }
        for (FormTable table : tables) {
            if (table == null || table.getForm() == null || table.getForm().getId() == null) {
                continue;
            }
            String name = checkedTable(ColumnNameResolver.trimToNull(table.getTableName()));
            if (name == null) {
                continue;
            }
            Long id = table.getForm().getId();
            boolean isActive = table.getDeleteState() == null || table.getDeleteState() == 0;
            String previous = names.get(id);
            if (previous == null || (!active.get(id) && isActive)) {
                names.put(id, name);
                active.put(id, isActive);
            } else if (!previous.equals(name) && active.get(id) == isActive) {
                throw new WorkflowEsbException(WorkflowErrors.FIELD_METADATA_INVALID, "表单 " + id + " 对应多个物理表");
            }
        }
        return names;
    }

    /**
     * 查询字段的物理列信息（form_table_field），键为字段 ID。
     *
     * @param fieldIds 字段 ID
     * @param employee 用户上下文
     * @return 字段 ID → 物理列信息
     */
    private Map<Long, FormTableField> physicalFields(List<Long> fieldIds, SimpleEmployee employee) {
        Map<Long, FormTableField> map = new LinkedHashMap<>();
        if (fieldIds.isEmpty()) {
            return map;
        }
        List<FormTableField> list = formTableFieldService.getByFieldIds(fieldIds, employee);
        if (list == null) {
            return map;
        }
        for (FormTableField item : list) {
            if (item != null && item.getFormField() != null && item.getFormField().getId() != null) {
                map.put(item.getFormField().getId(), item);
            }
        }
        return map;
    }

    /**
     * 解析主表数据库表名：优先物理表元数据，其次表单数据和主表字段所属表单。
     *
     * @param formId 主表单 ID
     * @param meta 物理元数据
     * @param formData 表单数据
     * @param fields 字段定义
     * @return 主表名；均未返回时为 null
     */
    private String mainTableName(long formId, Metadata meta, FormData formData, List<FormField> fields) {
        String name = meta.tables.get(formId);
        if (name != null) {
            return name;
        }
        name = tableName(formData.getForm());
        if (name != null) {
            return name;
        }
        for (FormField field : fields) {
            if (field != null && field.getSubForm() == null) {
                name = tableName(field.getForm());
                if (name != null) {
                    return name;
                }
            }
        }
        return null;
    }

    /**
     * 构造字段信息。
     *
     * @param field 平台字段定义
     * @param mainTableName 主表名
     * @param meta 物理元数据
     * @return 字段信息
     */
    private WorkflowFieldInfo toInfo(FormField field, String mainTableName, Metadata meta) {
        WorkflowFieldInfo info = new WorkflowFieldInfo();
        info.setFieldId(field.getId());
        info.setTitle(field.getTitle());
        info.setDataKey(field.getDataKey());
        info.setColumnName(ColumnNameResolver.resolve(field, meta.fields.get(field.getId())));
        info.setComponentKey(field.getComponentKey());
        info.setDataType(field.getDataType() == null ? null : field.getDataType().name());
        info.setRequired(field.isRequired());
        SubForm subForm = field.getSubForm();
        if (subForm == null) {
            info.setTableName(mainTableName);
        } else {
            info.setDetail(true);
            info.setSubFormId(subForm.getId());
            info.setSubFormDataKey(subForm.getDataKey());
            info.setTableName(detailTableName(subForm, meta.fields.get(field.getId()), meta));
        }
        return info;
    }

    /**
     * 把平台单元格数据按字段定义转换为主表 Map 和明细行，值与显示内容分别写入两套结构。
     *
     * @param result 查询结果
     * @param fieldsById 字段 ID → 字段定义
     * @param formData 平台表单数据
     * @param meta 物理元数据
     */
    private void convertData(WorkflowEsbForm result, Map<Long, FormField> fieldsById, FormData formData,
                             Metadata meta) {
        Map<String, Object> mainData = result.getMainData();
        Map<String, Object> mainText = result.getMainText();
        String dataId = String.valueOf(result.getDataId());
        putUnique(mainData, WorkflowEsbForm.ID_KEY, dataId, "主表");
        mainText.put(WorkflowEsbForm.ID_KEY, dataId);
        Map<String, TreeMap<Long, Map<String, Object>>> rowsByTable = new LinkedHashMap<>();
        Map<String, TreeMap<Long, Map<String, Object>>> textRowsByTable = new LinkedHashMap<>();
        Map<String, String> attachmentUrls = new LinkedHashMap<>();
        for (WorkflowFieldInfo info : result.getFields()) {
            if (!info.isDetail() && info.getColumnName() != null && "FileComponent".equals(info.getComponentKey())) {
                attachmentUrls.put(info.getColumnName() + "_url", null);
            }
            if (info.isDetail() && info.getTableName() != null && !rowsByTable.containsKey(info.getTableName())) {
                rowsByTable.put(info.getTableName(), new TreeMap<Long, Map<String, Object>>());
                textRowsByTable.put(info.getTableName(), new TreeMap<Long, Map<String, Object>>());
            }
        }
        List<FormDataDetail> details = formData.getDataDetails() == null
                ? new ArrayList<FormDataDetail>() : formData.getDataDetails();
        for (FormDataDetail detail : details) {
            if (detail == null || detail.getFormField() == null || detail.getFormField().getId() == null) {
                continue;
            }
            FormField field = fieldsById.get(detail.getFormField().getId());
            if (field == null) {
                continue;
            }
            FormTableField physical = meta.fields.get(field.getId());
            String column = ColumnNameResolver.resolve(field, physical);
            if (column == null) {
                throw new WorkflowEsbException(WorkflowErrors.FIELD_METADATA_INVALID,
                        "字段 " + field.getId() + " 有数据但平台未返回数据库列名");
            }
            Object[] cell = valueOf(detail);
            SubForm subForm = field.getSubForm() != null ? field.getSubForm() : detail.getSubForm();
            if (subForm == null) {
                putUnique(mainData, column, cell[0], "主表");
                mainText.put(column, cell[1]);
                String urlColumn = column + "_url";
                if (attachmentUrls.containsKey(urlColumn) || hasFileData(detail)) {
                    attachmentUrls.put(urlColumn, downloadUrls(detail, result.getTenantKey()));
                }
                continue;
            }
            String table = detailTableName(subForm, physical, meta);
            if (table == null) {
                throw new WorkflowEsbException(WorkflowErrors.FIELD_METADATA_INVALID, "明细表 " + subForm.getId() + " 未返回数据库表名");
            }
            Long rowIndex = detail.getDataIndex();
            if (rowIndex == null || rowIndex < 0) {
                throw new WorkflowEsbException(WorkflowErrors.FIELD_METADATA_INVALID, "明细表 " + table + " 的数据缺少有效行下标");
            }
            Map<String, Object> row = row(rowsByTable, table, rowIndex);
            Map<String, Object> textRow = row(textRowsByTable, table, rowIndex);
            if (detail.getRowId() != null) {
                putRowId(row, String.valueOf(detail.getRowId()), table);
                textRow.put(WorkflowEsbForm.ID_KEY, String.valueOf(detail.getRowId()));
            }
            putUnique(row, column, cell[0], table);
            if ("FileComponent".equals(field.getComponentKey()) || hasFileData(detail)) {
                putUnique(row, column + "_url", downloadUrls(detail, result.getTenantKey()), table);
            }
            textRow.put(column, cell[1]);
        }
        attachmentUrls.forEach((column, urls) -> putUnique(mainData, column, urls, "主表"));
        for (Map.Entry<String, TreeMap<Long, Map<String, Object>>> entry : rowsByTable.entrySet()) {
            result.getDetailData().put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
            result.getDetailText().put(entry.getKey(),
                    new ArrayList<>(textRowsByTable.get(entry.getKey()).values()));
        }
    }

    /** 兼容字段定义缺少组件标识但单元格明确声明为文件的数据。 */
    private boolean hasFileData(FormDataDetail detail) {
        return (detail.getFileDetails() != null && !detail.getFileDetails().isEmpty())
                || (detail.getDataOptions() != null && detail.getDataOptions().stream()
                .anyMatch(option -> option != null && option.getType() == ModuleSource.file));
    }

    /** 按 valueOf 的附件来源和顺序提取下载地址。 */
    private String downloadUrls(FormDataDetail detail, String tenantKey) {
        List<String> urls = new ArrayList<>();
        List<FormDataOption> options = detail.getDataOptions();
        if (options != null && !options.isEmpty()) {
            for (FormDataOption option : options) {
                if (option != null && option.getOptionObj() != null) {
                    Object source = option.getOptionObj();
                    Object json = source instanceof String && JSON.isValidObject((String) source)
                            ? JSON.parseObject((String) source) : JSON.toJSON(source);
                    if (json instanceof JSONObject) {
                        addDownloadUrl(urls, ((JSONObject) json).getString("url"), tenantKey, option.getContent());
                    }
                }
            }
        } else if (detail.getFileDetails() != null) {
            for (FileDetail file : detail.getFileDetails()) {
                if (file != null) {
                    addDownloadUrl(urls, file.getFileUrl(), tenantKey, file.getFileName());
                }
            }
        }
        return join(urls);
    }

    private void addDownloadUrl(List<String> urls, String url, String tenantKey, String fileName) {
        if (ColumnNameResolver.trimToNull(url) == null || ColumnNameResolver.trimToNull(fileName) == null) {
            return;
        }
        if (fileDownloadService == null) {
            throw new WorkflowEsbException(WorkflowErrors.PLATFORM_UNAVAILABLE, "附件下载地址需要文件下载服务");
        }
        add(urls, fileDownloadService.getDownloadUrl(url, tenantKey, fileName));
    }

    /**
     * 取得或创建指定明细表的指定行。
     *
     * @param rowsByTable 表名 → 行下标 → 行
     * @param table 明细表名
     * @param rowIndex 行下标
     * @return 行
     */
    private Map<String, Object> row(Map<String, TreeMap<Long, Map<String, Object>>> rowsByTable, String table,
                                    Long rowIndex) {
        TreeMap<Long, Map<String, Object>> rows = rowsByTable.get(table);
        if (rows == null) {
            rows = new TreeMap<>();
            rowsByTable.put(table, rows);
        }
        Map<String, Object> row = rows.get(rowIndex);
        if (row == null) {
            row = new LinkedHashMap<>();
            rows.put(rowIndex, row);
        }
        return row;
    }

    /**
     * 取单元格的值和显示内容。
     * 选项型（下拉、单选、复选、人员、部门、附件等）：值为 optionId、显示为 content，多值以英文逗号拼接；
     * 无 dataOptions 的附件取 fileDetails 的 fileId / fileName；
     * 其余依次取 content、多行文本 dataText.content、contentObj。
     *
     * @param detail 平台单元格
     * @return [值, 显示内容]
     */
    static Object[] valueOf(FormDataDetail detail) {
        List<FormDataOption> options = detail.getDataOptions();
        if (options != null && !options.isEmpty()) {
            List<String> ids = new ArrayList<>();
            List<String> texts = new ArrayList<>();
            for (FormDataOption option : options) {
                if (option != null) {
                    add(ids, option.getOptionId());
                    add(texts, option.getContent());
                }
            }
            return new Object[]{join(ids), join(texts)};
        }
        List<FileDetail> files = detail.getFileDetails();
        if (files != null && !files.isEmpty()) {
            List<String> ids = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (FileDetail file : files) {
                if (file != null) {
                    add(ids, file.getFileId() == null ? null : String.valueOf(file.getFileId()));
                    add(names, file.getFileName());
                }
            }
            return new Object[]{join(ids), join(names)};
        }
        if (detail.getContent() != null) {
            return new Object[]{detail.getContent(), detail.getContent()};
        }
        FormDataText text = detail.getDataText();
        if (text != null && text.getContent() != null) {
            return new Object[]{text.getContent(), text.getContent()};
        }
        Object obj = detail.getContentObj();
        return new Object[]{obj, obj == null ? null : String.valueOf(obj)};
    }

    /**
     * 将非空文本去除首尾空白后加入列表。
     *
     * @param list 目标列表
     * @param value 待加入的文本，null 或空白时忽略
     */
    private static void add(List<String> list, String value) {
        if (value != null && !value.trim().isEmpty()) {
            list.add(value.trim());
        }
    }

    /**
     * 以英文逗号拼接多个值。
     *
     * @param values 待拼接的值
     * @return 拼接结果；列表为空时为 null
     */
    private static String join(List<String> values) {
        if (values.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(value);
        }
        return sb.toString();
    }

    /**
     * 写入列值；同一行出现重复列时报错，避免静默覆盖。
     *
     * @param row 主表或明细行
     * @param column 数据库列名
     * @param value 值
     * @param table 表名，用于错误说明
     */
    private void putUnique(Map<String, Object> row, String column, Object value, String table) {
        if (row.containsKey(column)) {
            throw new WorkflowEsbException(WorkflowErrors.FIELD_METADATA_INVALID, table + " 同一行出现重复数据库列：" + column);
        }
        row.put(column, value);
    }

    /**
     * 写入明细行记录 ID；同一行的多个单元格应返回一致的行 ID。
     *
     * @param row 明细行
     * @param rowId 行记录 ID
     * @param table 表名
     */
    private void putRowId(Map<String, Object> row, String rowId, String table) {
        Object previous = row.get(WorkflowEsbForm.ID_KEY);
        if (previous == null) {
            row.put(WorkflowEsbForm.ID_KEY, rowId);
        } else if (!previous.equals(rowId)) {
            throw new WorkflowEsbException(WorkflowErrors.FIELD_METADATA_INVALID, table + " 同一行返回了不一致的行 ID");
        }
    }

    /**
     * 解析明细表数据库表名：优先按明细表 ID 匹配 form_table，其次字段物理信息中的表名，最后 SubForm#getTableName。
     *
     * @param subForm 明细表引用（运行时可能只带 id）
     * @param physical 字段物理列信息，可为 null
     * @param meta 物理元数据
     * @return 明细表名；均未返回时为 null
     */
    private String detailTableName(SubForm subForm, FormTableField physical, Metadata meta) {
        if (subForm.getId() != null) {
            String name = meta.tables.get(subForm.getId());
            if (name != null) {
                return name;
            }
        }
        String name = physical == null ? null : checkedTable(ColumnNameResolver.trimToNull(physical.getTempContent()));
        if (name != null) {
            return name;
        }
        return checkedTable(ColumnNameResolver.trimToNull(subForm.getTableName()));
    }

    /**
     * 读取并校验主表名。
     *
     * @param form 表单定义
     * @return 主表名；未返回时为 null
     */
    private String tableName(Form form) {
        return form == null ? null : checkedTable(ColumnNameResolver.trimToNull(form.getTableName()));
    }

    /**
     * 校验表名格式。
     *
     * @param name 表名
     * @return 原表名或 null
     */
    private String checkedTable(String name) {
        if (name != null && !ColumnNameResolver.isSafeIdentifier(name)) {
            throw new WorkflowEsbException(WorkflowErrors.FIELD_METADATA_INVALID, "平台返回的数据库表名格式无效");
        }
        return name;
    }

    /** 一次查询内的物理表、物理列元数据。 */
    private static final class Metadata {
        /** form_table.form_id → 物理表名。 */
        private final Map<Long, String> tables;
        /** 字段 ID → 物理列信息。 */
        private final Map<Long, FormTableField> fields;

        /**
         * 创建一次查询的物理元数据。
         *
         * @param tables form_table.form_id → 物理表名
         * @param fields 字段 ID → 物理列信息
         */
        Metadata(Map<Long, String> tables, Map<Long, FormTableField> fields) {
            this.tables = tables;
            this.fields = fields;
        }
    }
}
