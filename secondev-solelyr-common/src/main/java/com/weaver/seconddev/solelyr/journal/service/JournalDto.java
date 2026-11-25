package com.weaver.seconddev.solelyr.journal.service;

import com.weaver.seconddev.solelyr.journal.entity.JournalUnit;
import com.weaver.seconddev.solelyr.journal.exception.JournalErrors;
import com.alibaba.fastjson2.JSON;
import com.weaver.ebuilder.form.client.entity.data.*;
import com.weaver.ebuilder.form.client.entity.field.FieldsQueryDto;
import com.weaver.ebuilder.form.client.entity.field.ModuleField;
import com.weaver.ebuilder.form.client.entity.field.enums.FieldOperationTypeEnum;
import com.weaver.ebuilder.form.client.entity.obj.Obj;
import com.weaver.ebuilder.form.client.service.data.RemoteSimpleDataService;
import com.weaver.ebuilder.form.client.service.emobile.IEtFormDatasetService;
import com.weaver.ebuilder.teams.etform.base.query.*;
import com.weaver.ebuilder.teams.etform.org.bean.EBSimpleEmployee;
import com.weaver.seconddev.solelyr.workflow.exception.WorkflowEsbException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @USER: solelyr
 * @DATE: 2026/11/23 09:21
 * @DESCRIPTION: 在当前租户读取表单与下拉元数据，通过 E10 数据集和表单 RPC 读写数据
 */
public class JournalDto {
    /** E10 表单目录、字段元数据及记录查询 RPC。 */
    private final IEtFormDatasetService datasetService;
    /** 对应表单所属应用的 E10 数据新增与更新 RPC。 */
    private final RemoteSimpleDataService formDataService;
    /** 从表单目录读取的对象 ID，供 getFields、getDatas 及写入请求使用。 */
    private final long formObjectId;
    /** OA 物理主表名，用于定位目标日志表或幂等状态表。 */
    private final String tableName;
    /** 当前主表的 dataKey 到字段元数据映射，用于解析字段 ID 和选项。 */
    private final Map<String, List<ModuleField>> fieldsByKey = new LinkedHashMap<>();

    public JournalDto(IEtFormDatasetService datasetService, RemoteSimpleDataService formDataService, String appId,
                      String tenant, String tableName) {
        this.datasetService = datasetService;
        this.formDataService = formDataService;
        this.tableName = tableName;
        List<Obj> catalog = datasetService.getTables(appId);
        List<Obj> matches = catalog == null ? Collections.emptyList() : catalog.stream()
                .filter(o -> tableName.equals(o.getTableName()) && tenant.equals(o.getTenantKey()) && o.getDeleteType() == 0)
                .collect(Collectors.toList());
        if (matches.size() != 1 || matches.get(0).getId() == null) {
            throw invalid("当前租户的表单不存在或不唯一");
        }
        formObjectId = matches.get(0).getId();
        List<ModuleField> metadata = datasetService.getFields(new FieldsQueryDto("", formObjectId, "1", true));
        if (metadata != null) {
            for (ModuleField field : metadata) {
                String key = field.getDataKey();
                if (key == null || key.isEmpty()) {
                    key = field.getColumnName();
                }
                if (Boolean.TRUE.equals(field.getMainField()) && Long.valueOf(formObjectId).equals(field.getObjId())
                        && tableName.equals(field.getTableName())) {
                    fieldsByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(field);
                }
            }
        }
    }

    private ModuleField field(String key) {
        List<ModuleField> matches = fieldsByKey.get(key);
        if (matches == null || matches.size() != 1 || matches.get(0).getFieldId() == null) {
            throw invalid("无法解析字段 " + key);
        }
        return matches.get(0);
    }

    public String option(String key, String name) {
        ModuleField field = field(key);
        Object options = field.getConfig() == null ? null : field.getConfig().get("options");
        if (!(options instanceof List) || ((List<?>) options).isEmpty()) {
            options = field.getFieldInfo() == null ? null : field.getFieldInfo().get("options");
        }
        String found = null;
        if (options instanceof List) {
            for (Object item : (List<?>) options) {
                Map<?, ?> option = (Map<?, ?>) item;
                if (!name.equals(option.get("name"))) {
                    continue;
                }
                Object extra = option.get("extra");
                if (disabled(option) || (extra instanceof String && !((String) extra).trim().isEmpty()
                        && disabled(JSON.parseObject((String) extra)))) {
                    continue;
                }
                Object value = option.get("value");
                if (value == null) {
                    value = option.get("valueKey");
                }
                if (found != null || value == null || value.toString().isEmpty()) {
                    throw invalid("无效或重复选项 " + key + "/" + name);
                }
                found = value.toString();
            }
        }
        if (found == null) {
            throw invalid("缺少启用的选项 " + key + "/" + name);
        }
        return found;
    }

    private static boolean disabled(Map<?, ?> option) {
        for (String key : Arrays.asList("archive", "other")) {
            String flag = String.valueOf(option.get(key));
            if ("1".equals(flag) || "true".equalsIgnoreCase(flag)) {
                return true;
            }
        }
        return false;
    }

    public List<Map<String, String>> find(JournalUnit unit, String key, String... readKeys) {
        Query query = new Query();
        query.setPageSize(100);
        query.setStatCount(true);
        OrderField order = new OrderField();
        order.setObjId(formObjectId);
        order.setFieldId("id");
        order.setIsAsc(true);
        query.setOrderFields(Collections.singletonList(order));
        ConditionTreeDto root = new ConditionTreeDto();
        root.setIsParent(true);
        root.setRelationship("1");
        String target = option("mbxt", unit.system);
        root.setConditionList(Arrays.asList(equal("mdj", key), equal("jkcz", unit.operation), equal("mbxt", target)));
        query.setConditionTrees(root);
        Set<String> selected = new LinkedHashSet<>(Arrays.asList("mdj", "jkcz", "mbxt"));
        selected.addAll(Arrays.asList(readKeys));
        Map<String, ModuleField> readFields = new LinkedHashMap<>();
        selected.forEach(k -> readFields.put(k, field(k)));
        query.setQueryFields(readFields.values().stream().map(f -> new QueryField(f.getFieldId()))
                .collect(Collectors.toList()));
        EBSimpleEmployee user = new EBSimpleEmployee();
        user.setTenantKey(unit.tenant);
        user.setEmployeeId(Long.toString(unit.employeeId));
        query.setEbSimpleEmployee(user);
        query.setEbuilderDataConfig(Collections.singletonMap("dataSetup", Collections.singletonMap("dataPermission", "ALL")));
        List<Map<String, String>> result = new ArrayList<>();
        int count = -1;
        int seen = 0;
        for (int page = 1; count < 0 || seen < count; page++) {
            query.setPageNo(page);
            String encoded = Base64.getUrlEncoder().encodeToString(JSON.toJSONBytes(query));
            Map<String, Object> response = datasetService.getDatas(formObjectId, "", encoded, "");
            if (response == null || !(response.get("list") instanceof List)) {
                throw invalid("查询未返回完整数据");
            }
            int total = Integer.parseInt(String.valueOf(response.get("count")));
            List<?> rows = (List<?>) response.get("list");
            if (total < 0 || (count >= 0 && count != total) || rows.size() != Math.min(total - seen, query.getPageSize())) {
                throw invalid("查询分页结果不完整");
            }
            count = total;
            seen += rows.size();
            for (Object item : rows) {
                Map<?, ?> row = (Map<?, ?>) item;
                Map<String, String> values = new LinkedHashMap<>();
                values.put("id", String.valueOf(row.get("id")));
                for (Map.Entry<String, ModuleField> entry : readFields.entrySet()) {
                    ModuleField field = entry.getValue();
                    Object value = row.get(field.getFieldId());
                    if ("Select".equals(field.getComponentKey())) {
                        value = selection(value);
                    }
                    if (value != null) {
                        values.put(entry.getKey(), value.toString());
                    }
                }
                if (!key.equals(values.get("mdj")) || !unit.operation.equals(values.get("jkcz"))
                        || (row.get("tenant_key") != null && !unit.tenant.equals(row.get("tenant_key")))
                        || (row.get("obj_id") != null && !Long.toString(formObjectId).equals(row.get("obj_id").toString()))
                        || !values.get("id").matches("[1-9][0-9]*") || !target.equals(values.get("mbxt"))) {
                    throw invalid("返回行与请求的业务范围不符，请核对记录及目标系统选项");
                }
                result.add(values);
            }
        }
        return result;
    }

    private ConditionTreeDto equal(String key, String value) {
        ConditionTreeDto condition = new ConditionTreeDto();
        condition.setIsParent(false);
        condition.setObjId(Long.toString(formObjectId));
        condition.setConditionId(field(key).getFieldId());
        condition.setConditionType("String");
        condition.setCompareType("eq");
        condition.setConditionValue(value);
        return condition;
    }

    private static Object selection(Object value) {
        if (value instanceof List) {
            List<?> items = (List<?>) value;
            value = items.size() == 1 ? items.get(0) : null;
        }
        if (value instanceof Map) {
            return ((Map<?, ?>) value).get("id");
        }
        return value instanceof SimpleItem ? ((SimpleItem) value).getId() : value;
    }

    public String write(JournalUnit unit, String id, Map<String, String> values) {
        EBDataChangeReqDto request = new EBDataChangeReqDto();
        request.setHeader(new EBDataReqHeader(Long.toString(formObjectId), Long.toString(unit.employeeId), unit.tenant));
        EBDataReqOperation operation = new EBDataReqOperation();
        operation.setReqType(FieldOperationTypeEnum.dataKey);
        operation.setAsyncPostProcess(false);
        operation.setCheckRight(true);
        operation.setDoActoin(false);
        operation.setDoObjAction(false);
        request.setOperation(operation);
        List<EBDataReqDetailDto> details = new ArrayList<>();
        if (id != null) {
            operation.setUpdateType(EBDataUpdateType.ids);
            details.add(detail("id", id));
        }
        values.forEach((key, value) -> {
            if (value != null) {
                details.add(detail(key, value));
            }
        });
        EBDataReqDto row = new EBDataReqDto();
        row.setMainDatas(details);
        request.setDatas(Collections.singletonList(row));
        EBDataChangeResult result = id == null ? formDataService.saveFormData(request) : formDataService.updateFormData(request);
        if (result == null || !Boolean.TRUE.equals(result.getStatus()) || result.getDataIds() == null
                || result.getDataIds().size() != 1) {
            throw invalid("写入未获确认，请核对记录");
        }
        String saved = result.getDataIds().get(0);
        if (saved == null || !saved.matches("[1-9][0-9]*") || (id != null && !id.equals(saved))) {
            throw invalid("写入回执的记录编号无效");
        }
        return saved;
    }

    private static EBDataReqDetailDto detail(String key, String value) {
        EBDataReqDetailDto field = new EBDataReqDetailDto();
        field.setDataKey(key);
        field.setContent(value);
        return field;
    }

    private WorkflowEsbException invalid(String reason) {
        return new WorkflowEsbException(JournalErrors.STORAGE, "OA 表单 " + tableName + "：" + reason);
    }
}
