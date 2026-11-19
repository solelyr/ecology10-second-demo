package com.weaver.seconddev.chatgpt.seconddev.demo.supplier.service;

import com.weaver.common.form.data.FormData;
import com.weaver.common.form.data.detail.FormDataDetail;
import com.weaver.common.form.metadata.field.FormField;
import com.weaver.common.form.metadata.subform.SubForm;
import com.weaver.framework.rpc.annotation.RpcReference;
import com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.action.SupplierSubmission;
import com.weaver.teams.domain.user.SimpleEmployee;
import com.weaver.workflow.common.constant.formdef.WfpRelateFormType;
import com.weaver.workflow.common.entity.formdef.RequestFormInfoEntity;
import com.weaver.workflow.common.entity.formdef.WfpRelateFormEntity;
import com.weaver.workflow.common.entity.rpc.WfcRequestRpcEntity;
import com.weaver.workflow.core.api.rest.publicapi.WfcFormFieldRest;
import com.weaver.workflow.core.api.rest.publicapi.WfcGetRequestDataRest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * @USER: solelyr
 * @DATE: 2026/11/09 22:00
 * @DESCRIPTION: 按流程实例 ID 查询供应商表单，并按 dataKey 还原主明细数据
 */
@Component
public final class SupplierFormReader2 {
    /** 供应商表单所属的 E10 应用 ID。 */
    private static final String E10_APP_ID = "9564";

    @RpcReference
    private WfcGetRequestDataRest requestRest;

    @RpcReference
    private WfcFormFieldRest fieldRest;

    private final String appId;

    /**
     * 使用本类的 E10 应用 ID 创建表单读取组件。
     */
    public SupplierFormReader2() {
        this(E10_APP_ID);
    }

    /**
     * 读取供应商表单所属的 E10 应用配置。
     *
     * @param appId 供应商表单所属 E10 应用 ID
     */
    public SupplierFormReader2(String appId) {
        this.appId = appId.trim();
    }

    /**
     * 查询流程绑定的 eBuilder 记录并组装供应商映射输入。
     *
     * @param requestId 当前流程实例 ID
     * @param employee 当前用户和租户上下文
     * @return 主明细数据、流程编号和回写标识
     */
    public SupplierSubmission read(long requestId, SimpleEmployee employee) {
        if (!appId.matches("[1-9][0-9]{0,29}")) {
            throw new com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException(com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException.Kind.CONFIGURATION, "请填写供应商表单所属的 E10_APP_ID");
        }
        WfcRequestRpcEntity request = requestRest.getRequestEntity(requestId, employee);
        if (request == null || request.getRequestId() != requestId || request.getWorkflowId() <= 0) {
            throw new com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException(com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException.Kind.VALIDATION, "未找到当前流程实例");
        }
        RequestFormInfoEntity relation = supplierForm(request.getFormInfos());
        long formId = relation.getRelateFormObj().getRelateKey();
        List<FormField> fields = fieldRest.findFieldByWorkflowId(request.getWorkflowId(), employee);
        FormData formData = fieldRest.getFormDataByRequestId(requestId, employee);

        SupplierSubmission input = new SupplierSubmission();
        input.setAppId(appId);
        input.setFormId(Long.toString(formId));
        input.setFormDataId(Long.toString(relation.getDataId()));
        input.setBillId(request.getRequestMark());
        populateFormData(input, formId, fields, formData);
        return input;
    }

    /**
     * 校验流程关联了单一 eBuilder 表单，并提供表单和记录 ID。
     *
     * @param forms 当前流程实例的关联表单
     * @return 含实际表单 ID 和记录 ID 的关联信息
     */
    private RequestFormInfoEntity supplierForm(List<RequestFormInfoEntity> forms) {
        if (forms == null || forms.size() != 1 || forms.get(0) == null) {
            throw new com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException(com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException.Kind.VALIDATION, "供应商流程必须关联一张 eBuilder 表单");
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
     * @param input 承接主明细数据的供应商动作上下文
     * @param formId 流程关联的表单 ID
     * @param fields 流程的表单字段定义
     * @param formData 流程表单记录
     */
    private void populateFormData(SupplierSubmission input, long formId, List<FormField> fields, FormData formData) {
        if (fields == null || fields.isEmpty() || formData == null || formData.getDataDetails() == null) {
            throw new com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException(com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException.Kind.VALIDATION, "未读取到供应商表单字段定义或数据");
        }
        Map<Long, FormField> fieldsById = new HashMap<>();
        Map<Long, Set<String>> detailFieldKeys = new HashMap<>();
        for (FormField field : fields) {
            if (field != null && field.getId() != null
                    && field.getDataKey() != null && !field.getDataKey().isEmpty()) {
                fieldsById.put(field.getId(), field);
                SubForm subForm = field.getSubForm();
                if (subForm != null && subForm.getId() != null && subForm.getId() > 0) {
                    detailFieldKeys.computeIfAbsent(subForm.getId(), table -> new HashSet<>()).add(field.getDataKey());
                }
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
                if (field.getFormId() == null || field.getFormId() == formId) {
                    putField(mainData, field.getDataKey(), detail.getContent());
                }
                continue;
            }
            Long subFormId = subForm.getId();
            Long rowIndex = detail.getDataIndex();
            if (subFormId == null || subFormId <= 0 || rowIndex == null || rowIndex < 0) {
                throw new com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException(com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException.Kind.VALIDATION, "明细字段缺少有效的表 ID 或行下标");
            }
            detailFieldKeys.computeIfAbsent(subFormId, table -> new HashSet<>()).add(field.getDataKey());
            Map<String, Object> row = rowsByTable.computeIfAbsent(subFormId, table -> new TreeMap<>())
                    .computeIfAbsent(rowIndex, index -> new LinkedHashMap<>());
            putField(row, field.getDataKey(), detailValue(detail));
        }
        Map<Long, List<Map<String, Object>>> detailData = new LinkedHashMap<>();
        for (Map.Entry<Long, Map<Long, Map<String, Object>>> table : rowsByTable.entrySet()) {
            detailData.put(table.getKey(), new ArrayList<>(table.getValue().values()));
        }
        input.setMainData(mainData);
//        input.setDetailData(detailData);
//        input.setDetailFieldKeys(detailFieldKeys);
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
