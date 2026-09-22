package com.weaver.seconddev.seconddev.action;

import cn.hutool.core.codec.Base64;
import com.alibaba.fastjson2.JSON;
import com.weaver.common.base.entity.result.WeaResult;
import com.weaver.common.form.api.rest.second.FormSecondServiceRest;
import com.weaver.common.form.data.FormData;
import com.weaver.common.form.data.second.FormDataSearchCondition;
import com.weaver.common.form.metadata.field.FormField;
import com.weaver.common.form.metadata.field.FormFieldBaseService;
import com.weaver.common.form.metadata.field.SimpleFormField;
import com.weaver.common.form.physical.dao.FormTableDataDao;
import com.weaver.common.form.physical.entity.FormTableField;
import com.weaver.common.form.physical.service.FormTableDataService;
import com.weaver.common.form.physical.service.FormTableFieldService;
import com.weaver.common.hrm.util.HrmContextUtil;
import com.weaver.common.hrm.util.Util;
import com.weaver.ebuilder.datasource.api.entity.ExecuteSqlEntity;
import com.weaver.ebuilder.datasource.api.enums.SourceType;
import com.weaver.ebuilder.datasource.api.service.DataSetService;
import com.weaver.esb.api.rpc.EsbServerlessRpcRemoteInterface;
import com.weaver.framework.rpc.annotation.RpcReference;
import com.weaver.framework.rpc.context.impl.TenantRpcContext;
import com.weaver.teams.domain.user.SimpleEmployee;
import com.weaver.verupgrade.soa.workflow.request.RequestInfo;
import com.weaver.workflow.common.entity.core.flow.RequestBaseInfoEntity;
import com.weaver.workflow.common.entity.org.WeaUser;
import com.weaver.workflow.common.entity.rpc.WfcRequestRpcEntity;
import com.weaver.workflow.core.api.rest.flow.entity.WfcRequestBaseDataEntity;
import com.weaver.workflow.core.api.rest.openApi.RequestOperateOpenApiRest;
import com.weaver.workflow.core.api.rest.publicapi.WfcFormFieldRest;
import com.weaver.workflow.core.api.rest.publicapi.WfcGetRequestDataRest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
/**
 * @DESCRIPTION:
 * @USER: solelyr
 * @DATE: 2026/5/27 下午2:06
 */
@Service("secondevWorkflowActionDemo")
public class WorkflowActionDemo implements EsbServerlessRpcRemoteInterface {
    private static final Logger log = LoggerFactory.getLogger(WorkflowActionDemo.class);

    @RpcReference
    WfcGetRequestDataRest wfcGetRequestDataRest; // https://weapp.eteams.cn/ecode/playground/doc/share/view/969524129164623906
    @RpcReference
    WfcFormFieldRest wfcFormFieldRest;
    @RpcReference(group = "workflow")
    FormSecondServiceRest formSecondServiceRest;
    @RpcReference
    RequestOperateOpenApiRest requestOperateOpenApiRest;
    @Autowired
    DataSetService dataSetService;
    @Autowired
    FormTableDataDao formTableDataDao;
    @Autowired
    FormTableDataService formTableDataService;
    @Autowired
    FormFieldBaseService formFieldBaseService;
    @Autowired
    RequestInfo requestInfoServer;

    @Override
    public WeaResult<Map<String, Object>> execute(Map<String, Object> params) {

        Long userId = TenantRpcContext.getEmployeeIdLong();
        String tenantKey = TenantRpcContext.getCurrentTenantKey();
        SimpleEmployee employee = HrmContextUtil.build(userId, tenantKey);

        log.error("WorkflowActionDemo-error->{}", JSON.toJSONString(params));
        Long requestId = Util.getLongValue(params.get("requestId"));
        Map<String, Object> result = new HashMap<>();

        WeaUser waUser = new WeaUser();
        waUser.setUserId(employee.getUserId());
        waUser.setTenantKey(tenantKey);

        WfcRequestRpcEntity requestEntity = wfcGetRequestDataRest.getRequestEntity(requestId, employee);
        RequestBaseInfoEntity requestBaseInfo = wfcGetRequestDataRest.getRequestBaseInfoByRequestId(requestId,employee);
        WfcRequestBaseDataEntity WfcRequestBaseDataEntity = this.wfcGetRequestDataRest.getRequestBaseData(requestId, tenantKey);
        FormData formData1 = wfcFormFieldRest.getFormDataByRequestId(requestId,employee);
        List<FormField> fieldList = wfcFormFieldRest.findFieldByWorkflowId(requestEntity.getWorkflowId(), employee);
        String queryFlowInfoData = this.requestOperateOpenApiRest.queryFlowInfo(userId, tenantKey, requestId, "");

        Long relateFormId = requestEntity.getFormInfos().get(0).getRelateFormId();
        Long formId = requestEntity.getFormInfos().get(0).getRelateFormObj().getRelateKey();
        FormDataSearchCondition condition  = new FormDataSearchCondition();
        //表单Id，必传
        condition.setFormId(formId);
        //如果需要查询表的字段数据信息，请传入true，否则返回的FormData将没有数据信息
        condition.setContainsDataDetails(true);
        //是否获取选项型名称
        condition.setContainsOptionContent(true);
        //单条数据查询
        condition.setDataId(requestBaseInfo.getRequestId());
        /**
         * 传true则返回简易版格式数据，将主表和明细表数据分开
         *      主表数据取formData.getMainData()
         *      明细表数据取formData.getTableDatas()
         * 不传或false返回DataDetail格式数据
         *      数据取formData.getDataDetails()
         */
        condition.setNeedSimpleFormData(true);
        //传true则返回主表/明细表的表名称，字段的data_key
        condition.setNeedDetailTableInfo(true);
        Map<String, Object> map = formSecondServiceRest.getFormData(condition ,employee);
//        FormTableData
        log.error("WorkflowActionDemo-formData->{}", JSON.toJSONString(requestEntity));
        Map<String, Object> sqlData = doSql();

        List<SimpleFormField> simpleFormFieldList = formFieldBaseService.listSimpleFieldsByFormId(formId,0,employee);
        List<SimpleFormField> mainFieldList = simpleFormFieldList.stream().filter(i->i.getSubFormId() == null).collect(Collectors.toList());
//        List<SimpleFormField> listMultiLangFieldsByFormId = formFieldBaseService.listMultiLangFieldsByFormId(formId,employee);
        List<Map<String, Object>> singleFormTableData = formTableDataDao.findSingleFormTableData(requestId,"ft_1280181291314798592",mainFieldList,0,mainFieldList,true,employee);

//        List<Map<String, Object>> singleFormTableData2 = formTableDataDao.findSingleFormTableData(requestId,"ft_1280181_mxb1",simpleFormFieldList,0,simpleFormFieldList,false,employee);

        FormData formData = formTableDataService.findFormDataNew(formId,requestId,employee);
        List<Map<String, Object>> datas = formTableDataDao.getFormData("ft_1280181291314798592",requestId,employee);

        RequestInfo requestInfo = requestInfoServer.getRequestInfo(requestId,userId);
        data(formId,requestId,employee);

        result.put("fieldList", fieldList);
        result.put("datas", datas);
        result.put("status", true);
        result.put("msg","success");
        result.put("params", params);
        result.put("sqlData", sqlData);


        return WeaResult.success(result);
    }

    private Map<String, Object> doSql(){
        //logic类型就传logic的groupId 然后 sourceType传LOGIC
        //external类型就传external的groupId 然后 sourceType传EXTERNAL
//        String groupId = "logic_groupid";
        String groupId = "weaver-formreport-service"; // SELECT * from ds_mark_service_relation ;
        ExecuteSqlEntity executeSqlEntity = new ExecuteSqlEntity();
        executeSqlEntity.setSql(Base64.encode("select * from ft_1280181291314798592 where id = '1281975423569231872'"));
        executeSqlEntity.setGroupId(groupId);
        executeSqlEntity.setSourceType(SourceType.LOGIC);
        Map<String, Object> map = dataSetService.executeSql(executeSqlEntity);
        log.error("map-executeSql->{}", JSON.toJSONString(map));
        return map;
    }

    @Autowired
    private FormTableFieldService formTableFieldService;
    private Map<String,List<Map<String, Object>>> data(Long formId,Long formDataId,SimpleEmployee user){
        // 参照对象 ： com.weaver.common.form.physical.service.imp.FormTableDataServiceImpl findFormDataNew(Long formId, Long formDataId, SimpleEmployee user)
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
        Map<String,List<Map<String, Object>>> dataMap = new HashMap();
        for(String tableName : subFormFieldIdsMap.keySet()) {
            Long finalFormId = formId;
            Boolean isMainTable = Objects.equals(finalFormId, tableNameAndFormIdMap.get(tableName));
            List<Map<String, Object>> formDataDetails = this.formTableDataDao.findAllFormData(formDataId, tableName, new ArrayList(),subFormFieldIdsMap.get(tableName), null, isMainTable, user);
            formDataDetails.removeIf(Objects::isNull);
            dataMap.put(tableName, formDataDetails);
            System.out.println("formDataDetails:"+JSON.toJSONString(formDataDetails));
        }
        return dataMap;
    }
}
