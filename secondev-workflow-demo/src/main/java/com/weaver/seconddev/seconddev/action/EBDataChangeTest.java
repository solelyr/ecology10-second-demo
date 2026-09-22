package com.weaver.seconddev.seconddev.action;

import com.weaver.ebuilder.form.client.entity.data.*;
import com.weaver.ebuilder.form.client.entity.field.enums.FieldOperationTypeEnum;
import com.weaver.ebuilder.form.client.service.data.RemoteSimpleDataService;
import com.weaver.publishkit.api.util.PublishKitRuntimeUtil;
import com.weaver.seconddev.util.EBDataReqDetailDtoDataKeyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @DESCRIPTION:
 * @USER: solelyr
 * @DATE: 2026/6/24 14:49
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
}
