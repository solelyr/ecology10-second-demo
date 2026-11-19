package com.weaver.seconddev.chatgpt.seconddev.integration.workflow;

import com.weaver.common.base.entity.result.WeaResult;
import com.weaver.common.form.data.FormData;
import com.weaver.common.form.data.detail.FormDataDetail;
import com.weaver.common.form.metadata.field.FormField;
import com.weaver.common.form.metadata.subform.SubForm;
import com.weaver.ebuilder.form.client.entity.obj.Obj;
import com.weaver.ebuilder.form.client.service.flow.EbuilderFlowForSpecializedApp;
import com.weaver.ebuilder.form.client.service.obj.RemoteEBObjService;
import com.weaver.framework.rpc.annotation.RpcReference;
import com.weaver.publishkit.api.util.PublishKitRuntimeUtil;
import com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException;
import com.weaver.teams.domain.user.SimpleEmployee;
import com.weaver.workflow.common.constant.formdef.WfpRelateFormType;
import com.weaver.workflow.common.entity.formdef.RequestFormInfoEntity;
import com.weaver.workflow.common.entity.formdef.WfpRelateFormEntity;
import com.weaver.workflow.common.entity.rpc.WfcRequestRpcEntity;
import com.weaver.workflow.core.api.rest.publicapi.WfcFormFieldRest;
import com.weaver.workflow.core.api.rest.publicapi.WfcGetRequestDataRest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @USER: solelyr
 * @DATE: 2026/11/10 20:38
 * @DESCRIPTION: 按流程实例 ID 查询关联的 eBuilder 表单，并按 dataKey 还原主明细数据
 */
@Component
public final class WorkflowFormReader {
    @RpcReference
    private WfcGetRequestDataRest requestRest;

    @RpcReference
    private WfcFormFieldRest fieldRest;

    @Autowired
    private PublishKitRuntimeUtil publishKit;

    /**
     * 按流程实例查询所属应用和关联表单数据。
     *
     * @param requestId 当前流程实例 ID
     * @param employee 当前用户和租户上下文
     * @return 主明细数据、应用 ID、流程编号及表单标识
     */
    public com.weaver.seconddev.chatgpt.seconddev.integration.workflow.WorkflowFormData readByRequestId(long requestId, SimpleEmployee employee) {
        return readByRequestId(requestId, employee, null);
    }

    /**
     * 查询流程绑定的 eBuilder 记录，按表和字段的 dataKey 返回已保存的主明细数据。
     *
     * @param requestId 当前流程实例 ID
     * @param employee 当前用户和租户上下文
     * @param appId 表单所属 E10 应用 ID，传 null 时按工作流 ID 查询
     * @return 主明细数据、应用 ID、流程编号及表单标识
     */
    public com.weaver.seconddev.chatgpt.seconddev.integration.workflow.WorkflowFormData readByRequestId(long requestId, SimpleEmployee employee, String appId) {
        if (requestId <= 0 || employee == null || employee.getEmployeeId() == null || employee.getEmployeeId() <= 0
                || employee.getTenantKey() == null || employee.getTenantKey().trim().isEmpty()) {
            throw new com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException(com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException.Kind.VALIDATION, "缺少有效的流程 ID、操作用户或租户");
        }
        if (appId != null && !appId.trim().matches("[1-9][0-9]{0,29}")) {
            throw new com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException(com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException.Kind.CONFIGURATION, "请填写表单所属的 E10 应用 ID");
        }
        WfcRequestRpcEntity request = requestRest.getRequestEntity(requestId, employee);
        if (request == null || request.getRequestId() != requestId || request.getWorkflowId() <= 0) {
            throw new com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException(com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException.Kind.VALIDATION, "未找到当前流程实例");
        }
        RequestFormInfoEntity relation = relatedForm(request.getFormInfos());
        long formId = relation.getRelateFormObj().getRelateKey();
        long workflowId = request.getWorkflowId();
        String resolvedAppId = appId == null ? resolveAppId(workflowId, employee) : appId.trim(); // 获取表单所有字段信息(根据工作流workflowId获取)
        List<FormField> fields = fieldRest.findFieldByWorkflowId(workflowId, employee);
        FormData formData = fieldRest.getFormDataByRequestId(requestId, employee); // 获取表单所有字段值(根据流程requestid获取)

        com.weaver.seconddev.chatgpt.seconddev.integration.workflow.WorkflowFormData result = new com.weaver.seconddev.chatgpt.seconddev.integration.workflow.WorkflowFormData();
        result.setAppId(resolvedAppId);
        result.setFormId(formId);
        result.setFormDataId(relation.getDataId());
        result.setRequestMark(request.getRequestMark());
        populateFormData(result, fields, formData, employee);
        return result;
    }

    /**
     * 沿用 E10 工作流浏览器的默认应用路由查询实际所属应用。
     *
     * @param workflowId 流程实例所属工作流 ID
     * @param employee 当前用户和租户上下文
     * @return eBuilder 应用 ID
     */
    private String resolveAppId(long workflowId, SimpleEmployee employee) {
        EbuilderFlowForSpecializedApp remote = publishKit.buildRpcService(
                EbuilderFlowForSpecializedApp.class, "ebuildercard", publishKit.getDefaultAppId());
        Map<Long, Long> appIds = remote.getAppIdByWorkflowIds(Collections.singletonList(workflowId), employee);
        Long appId = appIds == null ? null : appIds.get(workflowId);
        if (appId == null || appId <= 0) {
            throw new com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException(com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException.Kind.VALIDATION, "未查到工作流所属的 eBuilder 应用 ID：" + workflowId);
        }
        return appId.toString();
    }

    /**
     * 校验流程关联了单一 eBuilder 表单，并提供表单和记录 ID。
     *
     * @param forms 当前流程实例的关联表单
     * @return 含实际表单 ID 和记录 ID 的关联信息
     */
    private RequestFormInfoEntity relatedForm(List<RequestFormInfoEntity> forms) {
        if (forms == null || forms.size() != 1 || forms.get(0) == null) {
            throw new com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException(com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException.Kind.VALIDATION, "流程必须关联一张 eBuilder 表单");
        }
        RequestFormInfoEntity relation = forms.get(0);
        WfpRelateFormEntity form = relation.getRelateFormObj();
        if (form == null || form.getRelateType() != WfpRelateFormType.WEA_EBUILDER || form.getRelateKey() <= 0
                || relation.getDataId() <= 0) {
            throw new com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException(com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException.Kind.VALIDATION, "流程缺少有效的 eBuilder 表单 ID 或记录 ID");
        }
        return relation;
    }

    /**
     * 通过字段 ID 合并定义和值，分别还原主表及按行分组的明细。
     *
     * @param result 承接主明细数据的读取结果
     * @param fields 流程的表单字段定义
     * @param formData 流程表单记录
     * @param employee 当前用户和租户上下文
     */
    private void populateFormData(WorkflowFormData result, List<FormField> fields,
                                  FormData formData, SimpleEmployee employee) {
        if (fields == null || fields.isEmpty() || formData == null || formData.getDataDetails() == null) {
            throw new com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException(com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException.Kind.VALIDATION, "未读取到流程表单字段定义或数据");
        }
        Map<Long, FormField> fieldsById = new HashMap<>();
        Map<Long, String> tableKeys = new LinkedHashMap<>();
        for (FormField field : fields) {
            if (field != null && field.getId() != null
                    && field.getDataKey() != null && !field.getDataKey().isEmpty()) {
                fieldsById.put(field.getId(), field);
                registerTableKey(tableKeys, field.getSubForm());
            }
        }
        Map<String, Object> mainData = new HashMap<>();
        Map<Long, Map<Long, Map<String, Object>>> rowsByTable = new LinkedHashMap<>();
        for (FormDataDetail detail : formData.getDataDetails()) {
            if (detail == null || detail.getFormField() == null) {
                continue;
            }
            FormField field = fieldsById.get(detail.getFormField().getId());
            if (field == null) {
                continue;
            }
            SubForm subForm = detail.getSubForm() == null ? field.getSubForm() : detail.getSubForm();
            if (subForm == null) {
                if (field.getFormId() == null || field.getFormId() == result.getFormId()) {
                    putField(mainData, field.getDataKey(), detail.getContent());
                }
                continue;
            }
            Long subFormId = subForm.getId();
            Long rowIndex = detail.getDataIndex();
            if (subFormId == null || subFormId <= 0 || rowIndex == null || rowIndex < 0) {
                throw new com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException(com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException.Kind.VALIDATION, "明细字段缺少有效的表 ID 或行下标");
            }
            registerTableKey(tableKeys, subForm);
            Map<String, Object> row = rowsByTable.computeIfAbsent(subFormId, table -> new TreeMap<>())
                    .computeIfAbsent(rowIndex, index -> new LinkedHashMap<>());
            putField(row, field.getDataKey(), detailValue(detail));
        }
        Set<Long> missingTableIds = new LinkedHashSet<>(rowsByTable.keySet());
        missingTableIds.removeAll(tableKeys.keySet());
        if (!missingTableIds.isEmpty()) {
            loadTableKeys(result.getFormId(), employee, result.getAppId(), missingTableIds, tableKeys);
        }
        Map<String, List<Map<String, Object>>> detailData = new LinkedHashMap<>();
        for (Map.Entry<Long, String> table : tableKeys.entrySet()) {
            String key = table.getValue();
            if (detailData.containsKey(key)) {
                throw new com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException(com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException.Kind.VALIDATION, "多个明细表使用同一个 dataKey：" + key);
            }
            Map<Long, Map<String, Object>> rows = rowsByTable.remove(table.getKey());
            detailData.put(key, rows == null ? Collections.emptyList() : new ArrayList<>(rows.values()));
        }
        if (!rowsByTable.isEmpty()) {
            throw new com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException(com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException.Kind.VALIDATION, "E10 未返回明细表 dataKey：" + rowsByTable.keySet());
        }
        result.setMainData(mainData);
        result.setDetailData(detailData);
    }

    /**
     * 流程 RPC 只返回明细表 ID 时，通过 eBuilder 查询当前主表关联的表配置。
     *
     * @param formId 流程关联的内部表单 ID
     * @param employee 当前用户和租户上下文
     * @param appId 表单所属 E10 应用 ID
     * @param missingTableIds 需要补查 dataKey 的明细表 ID
     * @param tableKeys 本次已解析的明细表业务键
     */
    private void loadTableKeys(long formId, SimpleEmployee employee, String appId, Set<Long> missingTableIds,
                               Map<Long, String> tableKeys) {
        RemoteEBObjService remote = publishKit.buildRpcService(RemoteEBObjService.class, "ebuilderform", appId);
        String tenantKey = employee.getTenantKey();
        WeaResult<List<Obj>> result = remote.getObjListByFormId(Collections.singletonList(formId), tenantKey);
        List<Obj> masters = result == null || !result.isStatus() ? null : result.getData();
        if (masters == null || masters.size() != 1 || masters.get(0) == null || masters.get(0).getId() == null) {
            throw new com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException(com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException.Kind.VALIDATION, "无法通过内部 formId 定位唯一的 eBuilder 主表对象：" + formId);
        }
        List<Obj> tables = remote.getAllTablesByMasterId(masters.get(0).getId(), tenantKey);
        if (tables == null) {
            return;
        }
        Map<String, Long> idsByText = new HashMap<>();
        for (Long tableId : missingTableIds) {
            idsByText.put(tableId.toString(), tableId);
        }
        for (Obj table : tables) {
            if (table != null) {
                Long tableId = idsByText.get(table.getFormId());
                if (tableId == null && missingTableIds.contains(table.getId())) {
                    tableId = table.getId();
                }
                registerTableKey(tableKeys, tableId, table.getDataKey());
            }
        }
    }

    /**
     * 记录 E10 明细表 ID 与 dataKey 的对应关系。数据项只返回表 ID 时可复用定义里的键。
     *
     * @param tableKeys 本次读取发现的明细表业务键
     * @param table 字段定义或数据项返回的明细表引用
     */
    private void registerTableKey(Map<Long, String> tableKeys, SubForm table) {
        if (table != null) {
            registerTableKey(tableKeys, table.getId(), table.getDataKey());
        }
    }

    /**
     * 将同一个明细表 ID 关联到唯一的 dataKey。
     *
     * @param tableKeys 本次已解析的明细表业务键
     * @param tableId 明细表 ID
     * @param key E10 返回的业务键
     */
    private void registerTableKey(Map<Long, String> tableKeys, Long tableId, String key) {
        if (tableId == null || tableId <= 0 || key == null || key.trim().isEmpty()) {
            return;
        }
        String previous = tableKeys.putIfAbsent(tableId, key);
        if (previous != null && !previous.equals(key)) {
            throw new com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException(com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException.Kind.VALIDATION, "明细表返回了不一致的 dataKey：" + tableId);
        }
    }

    /**
     * 保存同一主表或明细行中的字段，防止重复字段覆盖原值。
     *
     * @param row 主表或一行明细
     * @param key 已核验的字段 dataKey
     * @param value E10 返回的原始字段值
     */
    private void putField(Map<String, Object> row, String key, Object value) {
        if (row.containsKey(key)) {
            throw new com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException(IntegrationException.Kind.VALIDATION, "同一行字段返回了重复数据：" + key);
        }
        row.put(key, value);
    }

    /**
     * 取明细单元格的原始值，保留选项、附件和多行文本的 E10 数据结构。
     *
     * @param detail E10 表单单元格
     * @return 原始字段值
     */
    private Object detailValue(FormDataDetail detail) {
        if (detail.getDataOptions() != null && !detail.getDataOptions().isEmpty()) {
            return detail.getDataOptions();
        }
        if (detail.getDataText() != null) {
            return detail.getDataText();
        }
        if (detail.getContentObj() != null) {
            return detail.getContentObj();
        }
        return detail.getContent();
    }
}
