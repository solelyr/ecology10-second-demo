package com.weaver.seconddev.chatgpt.seconddev.demo.supplier.service.callback;

import com.weaver.ebuilder.form.client.entity.data.*;
import com.weaver.ebuilder.form.client.entity.field.enums.FieldOperationTypeEnum;
import com.weaver.ebuilder.form.client.service.data.RemoteSimpleDataService;
import com.weaver.publishkit.api.util.PublishKitRuntimeUtil;
import com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.action.SupplierSubmission;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.response.VendorCreated;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * @USER: solelyr
 * @DATE: 2026/11/06 09:53
 * @DESCRIPTION: 使用已核验的 eBuilder RPC 更新当前供应商记录的返回编码与状态
 */
@Component
public final class E10SupplierSuccessCallback implements SupplierSuccessCallback {
    /** 第三方返回的供应商编码对应的 E10 回写 dataKey。 */
    private static final String VENDOR_CODE_FIELD = "";
    /** 同步状态对应的 E10 回写 dataKey。 */
    private static final String SYNC_STATUS_FIELD = "";

    private final PublishKitRuntimeUtil publishKit;

    /**
     * 创建 E10 表单回写组件。
     *
     * @param publishKit 用于获取 E10 表单 RPC 服务的运行时工具
     */
    public E10SupplierSuccessCallback(PublishKitRuntimeUtil publishKit) {
        this.publishKit = publishKit;
    }

    /**
     * 按内部表单 ID 和记录 ID 回写供应商编码及同步状态。执行时检查权限并关闭递归动作。
     *
     * @param source 当前 E10 表单记录及动作上下文
     * @param result 第三方确认的供应商新增结果
     * @param employeeId E10 操作用户 ID
     * @param tenantKey 当前租户标识
     */
    @Override
    public void afterSuccess(SupplierSubmission source, VendorCreated result, String employeeId, String tenantKey) {
        if (VENDOR_CODE_FIELD.isEmpty() || SYNC_STATUS_FIELD.isEmpty()) {
            throw new com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException(com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException.Kind.WRITEBACK, "第三方已新增供应商，但 E10 回写字段常量未填写");
        }
        RemoteSimpleDataService remote = publishKit.buildRpcService(RemoteSimpleDataService.class, "ebuilderform", source.getAppId());
        EBDataChangeReqDto request = new EBDataChangeReqDto();
        request.setHeader(new EBDataReqHeader(source.getFormId(), employeeId, tenantKey));
        EBDataReqOperation operation = new EBDataReqOperation();
        operation.setUpdateType(EBDataUpdateType.ids);
        operation.setReqType(FieldOperationTypeEnum.dataKey);
        operation.setAsyncPostProcess(false);
        operation.setCheckRight(true);
        // Prevent the demo write-back from recursively triggering the same Action.
        operation.setDoActoin(false);
        operation.setDoObjAction(false);
        request.setOperation(operation);
        List<EBDataReqDetailDto> main = new ArrayList<>();
        main.add(field("id", source.getFormDataId()));
        main.add(field(VENDOR_CODE_FIELD, result.getVendorCode()));
        main.add(field(SYNC_STATUS_FIELD, "SUCCESS"));
        EBDataReqDto data = new EBDataReqDto(); data.setMainDatas(main);
        request.setDatas(Collections.singletonList(data));
        EBDataChangeResult response = remote.updateFormData(request);
        if (response == null || !response.getStatus() || response.getDataIds() == null
                || !response.getDataIds().contains(source.getFormDataId()))
            throw new com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException(IntegrationException.Kind.WRITEBACK, "第三方已新增供应商，但 E10 表单更新未确认成功");
    }

    /**
     * 构造一个 E10 表单字段回写项。
     *
     * @param key 目标字段的 dataKey
     * @param value 写入字段的内容
     * @return 包含目标字段和值的回写项
     */
    private EBDataReqDetailDto field(String key, String value) {
        EBDataReqDetailDto field = new EBDataReqDetailDto();
        field.setDataKey(key);
        field.setContent(value);
        return field;
    }
}
