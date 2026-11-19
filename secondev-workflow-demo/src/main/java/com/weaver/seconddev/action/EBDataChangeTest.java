package com.weaver.seconddev.action;

import com.weaver.common.base.entity.result.WeaResult;
import com.weaver.ebuilder.coms.sdk.core.dto.config.common.QueryField;
import com.weaver.ebuilder.form.client.entity.data.*;
import com.weaver.ebuilder.form.client.entity.field.enums.FieldOperationTypeEnum;
import com.weaver.ebuilder.form.client.entity.obj.Obj;
import com.weaver.ebuilder.form.client.service.data.RemoteSimpleDataService;
import com.weaver.ebuilder.form.client.service.emobile.IEtFormDatasetService;
import com.weaver.ebuilder.form.client.service.obj.RemoteEBObjService;
import com.weaver.framework.rpc.annotation.RpcReference;
import com.weaver.publishkit.api.util.PublishKitRuntimeUtil;
import com.weaver.seconddev.util.EBDataReqDetailDtoDataKeyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.weaver.ebuilder.teams.etform.base.query.Query;

import java.util.*;

/**
 * @USER: solelyr
 * @DATE: 2026/6/24 14:49
 * @DESCRIPTION: 演示通过 eBuilder 服务保存表单数据
 */
@Component
public class EBDataChangeTest {
    @Autowired
    private PublishKitRuntimeUtil publishKitRuntimeUtil;

    public void doSave(){
        RemoteSimpleDataService remoteSimpleDataService = publishKitRuntimeUtil.buildRpcService(RemoteSimpleDataService.class, "ebuilderform", "1276486476802310144");

        EBDataChangeReqDto ebDataChangeReqDto = new EBDataChangeReqDto();
        // 构建基础参数; objId 表单id, operator 操作人, tenantKey 租户
        ebDataChangeReqDto.setHeader(new EBDataReqHeader("1276486515675119616", "1276438927336816657", "tzfalirdzv"));
        List<EBDataReqDto> datas = new ArrayList<>();

        // 若数据写入后就要从前台看到数据或者立即操作本次写入的数据, 需要调整后置处理为同步 权限处理完成再返回
        EBDataReqOperation operation = new EBDataReqOperation();
        operation.setAsyncPostProcess(true);
        operation.setReqType(FieldOperationTypeEnum.dataKey); // 设置dataKey的方式请求, 默认以fieldId请求方式
        ebDataChangeReqDto.setOperation(operation);

        // 数据1
        EBDataReqDto ebDataReqDto1 = new EBDataReqDto();
        // 主表数据参数
        List<EBDataReqDetailDto> mainData1 = new ArrayList<>();
        // 单行文本
        mainData1.add(EBDataReqDetailDtoDataKeyUtil.setData("dxwb", "单行文本数据1"));
        // 选择框根据选项名赋值
        mainData1.add(EBDataReqDetailDtoDataKeyUtil.setData("xl", "0"));
        ebDataReqDto1.setMainDatas(mainData1);

        // 明细数据参数
        Map<Long, List<List<EBDataReqDetailDto>>> detailDatas = new HashMap<>();
        // 多条明细数据
        List<List<EBDataReqDetailDto>> subFormDatas = new ArrayList<>();
        // 单明细数据不同字段值 - 明细数据1
        List<EBDataReqDetailDto> subFormRowData1 = new ArrayList<>();
        subFormRowData1.add(EBDataReqDetailDtoDataKeyUtil.setData("mxdx", "明细数据1-1"));
        subFormRowData1.add(EBDataReqDetailDtoDataKeyUtil.setData("mxxl", "0"));
        subFormDatas.add(subFormRowData1);
        // 单明细数据不同字段值 - 明细数据2
        List<EBDataReqDetailDto> subFormRowData2 = new ArrayList<>();
        subFormRowData2.add(EBDataReqDetailDtoDataKeyUtil.setData("mxdx", "明细数据2-1"));
        subFormRowData2.add(EBDataReqDetailDtoDataKeyUtil.setData("mxxl", "2"));
        subFormDatas.add(subFormRowData2);
        // 将明细数据设置到执行明细表中
        detailDatas.put(1284476348711911431L, subFormDatas);
        ebDataReqDto1.setDetailDatas(detailDatas);

        datas.add(ebDataReqDto1);
        ebDataChangeReqDto.setDatas(datas);
        EBDataChangeResult ebDataChangeResult = remoteSimpleDataService.saveFormData(ebDataChangeReqDto);
    }

    @RpcReference(group = "ebuilderform")
    IEtFormDatasetService iEtFormDatasetService; // https://weapp.eteams.cn/build/techdoc/wdoc/index.html#/public/repository/50d3476f-3a51-40ec-89c8-6574c3718fad/doc/746185198469464065
    public void doQuery(){
        Long mainId = 0L; // 表单id
        String module = ""; // 模块标识（可传空）
        Query query = new Query();
        String otherParm = "";
        Map<String, Object> data = iEtFormDatasetService.getDatas(mainId, module,query.toString(), otherParm);

    }

    @RpcReference(group = "ebuilderform")
    private RemoteEBObjService remoteEBObjService;
    public void objInfo(){
        /**
         * 根据表单id获取表单配置信息
         * @param objId     表单id
         * @param tenantKey 租户key
         * @return 表单配置信息
         */
        WeaResult<Obj> info = remoteEBObjService.getObjInfo( 0L,"tenantKey");

        /**
         * 按主表Id获取所有表
         * @param objId     表单id
         * @param tenantKey 租户Key
         * @return 主子表信息
         *  dataKey 表单key
         *  tableName 数据库表名
         */
        List<Obj> objList = remoteEBObjService.getAllTablesByMasterId(0L, "tenantKey");
    }
}
