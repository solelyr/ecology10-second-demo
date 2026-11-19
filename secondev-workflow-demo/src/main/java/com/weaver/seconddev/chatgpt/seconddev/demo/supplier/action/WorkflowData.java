package com.weaver.seconddev.chatgpt.seconddev.demo.supplier.action;

import com.alibaba.fastjson.JSON;
import com.weaver.common.base.entity.result.WeaResult;
import com.weaver.common.ei.utils.Util;
import com.weaver.common.form.api.rest.second.FormSecondServiceRest;
import com.weaver.common.form.data.second.FormDataSearchCondition;
import com.weaver.common.form.metadata.field.FormField;
import com.weaver.common.form.physical.dao.FormTableDataDao;
import com.weaver.common.form.physical.entity.FormTableField;
import com.weaver.common.form.physical.service.FormTableFieldService;
import com.weaver.common.hrm.util.HrmContextUtil;
import com.weaver.esb.api.rpc.EsbServerlessRpcRemoteInterface;
import com.weaver.framework.rpc.annotation.RpcReference;
import com.weaver.framework.rpc.context.impl.TenantRpcContext;
import com.weaver.teams.domain.user.SimpleEmployee;
import com.weaver.workflow.common.entity.rpc.WfcRequestRpcEntity;
import com.weaver.workflow.core.api.rest.publicapi.WfcFormFieldRest;
import com.weaver.workflow.core.api.rest.publicapi.WfcGetRequestDataRest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * @USER: solelyr
 * @DATE: 2026/11/10 22:55
 * @DESCRIPTION: 演示按流程实例查询表单定义并按物理表名整理数据
 */
@Service("secondevWorkflowData")
public class WorkflowData implements EsbServerlessRpcRemoteInterface {
    @RpcReference
    WfcGetRequestDataRest wfcGetRequestDataRest;

    @RpcReference
    WfcFormFieldRest wfcFormFieldRest;

    @Autowired
    private FormTableFieldService formTableFieldService;
    @Autowired
    FormTableDataDao formTableDataDao;
    @RpcReference(group = "workflow")
    FormSecondServiceRest formSecondServiceRest;

    @Override
    public WeaResult<Map<String, Object>> execute(Map<String, Object> params) {

        Long userId = TenantRpcContext.getEmployeeIdLong();
        String tenantKey = TenantRpcContext.getCurrentTenantKey();
        SimpleEmployee user = HrmContextUtil.build(userId, tenantKey);

        Long requestId = Util.getLongValue(params.get("requestId"));
        WfcRequestRpcEntity requestEntity = wfcGetRequestDataRest.getRequestEntity(requestId, user);
        Long formId = requestEntity.getFormInfos().get(0).getRelateFormObj().getRelateKey();
        List<FormTableField> fieldList = this.formTableFieldService.listFieldInfosByFormId(formId, true, user); // 获取所有的字段列表
        Map<String, List<FormTableField>> subFormFieldIdsMap = new HashMap();
        Map<String, Long> tableNameAndFormIdMap = new HashMap();

        for(FormTableField formTableField : fieldList) {
            String tableName = formTableField.getTempContent();
            if (!com.weaver.common.form.util.StringUtils.isEmpty(tableName)) {
                FormField formField = formTableField.getFormField();
                Long fId = formField.getSubForm() != null && formField.getSubForm().getId() != null ? formField.getSubForm().getId() : formField.getForm().getId();
                List<FormTableField> tableFields = (List)subFormFieldIdsMap.getOrDefault(tableName, new ArrayList());
                tableFields.add(formTableField);
                subFormFieldIdsMap.put(tableName, tableFields);
                tableNameAndFormIdMap.put(tableName, fId);
            }
        }
        Map<String,Object> dataMap = new HashMap(); // 最终返回的数据，key为表单名称，value为List<Map<String, Object>>。主表数据最终需要执行下get(0)来读取。

        for(String tableName : subFormFieldIdsMap.keySet()) {
            Long finalFormId = formId;
            Boolean isMainTable = Objects.equals(finalFormId, tableNameAndFormIdMap.get(tableName));
            List<Map<String, Object>> formDataDetails = this.formTableDataDao.findAllFormData(requestId, tableName, new ArrayList(),subFormFieldIdsMap.get(tableName), null, isMainTable, user);
            formDataDetails.removeIf(Objects::isNull);
            dataMap.put(tableName, formDataDetails);
        }

        Map<String, Object> formDataIdMap = wfcGetRequestDataRest.getRequestInfoAndFormDataIdMap(user, requestId);

        Map<String, Object> formDataNameMap = wfcGetRequestDataRest.getRequestInfoAndFormDataNameMap(user, requestId);

        test1(formId,requestId,user);

        return WeaResult.success(dataMap);
    }

    private void test1(long formId, long dataId, SimpleEmployee employee){
        FormDataSearchCondition condition = new FormDataSearchCondition();
        condition.setFormId(formId);
        condition.setContainsDataDetails(true);   // 必开，否则拿不到字段值
        condition.setContainsOptionContent(true);
        condition.setNeedSimpleFormData(true);    // 主表 getMainData()，明细 getTableDatas()
        condition.setNeedDetailTableInfo(true);   // 需要物理表名/data_key 时开
        condition.setDataId(dataId);

        Map<String, Object> result = formSecondServiceRest.getFormData(condition, employee);
        JSON.toJSONString("result:"+JSON.toJSONString(result));
    }
}
