package com.weaver.seconddev.chatgpt.seconddev.demo.supplier.service.callback;

import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.response.VendorCreated;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.action.SupplierSubmission;

/**
 * @USER: solelyr
 * @DATE: 2026/11/06 09:53
 * @DESCRIPTION: 定义第三方成功后的 E10 业务处理扩展点
 */
public interface SupplierSuccessCallback {

    /**
     * 在第三方新增供应商成功后回写 E10。
     *
     * @param source 当前 E10 表单记录及动作上下文
     * @param result 第三方确认的供应商新增结果
     * @param employeeId E10 操作用户 ID
     * @param tenantKey 当前租户标识
     */
    void afterSuccess(SupplierSubmission source, VendorCreated result, String employeeId, String tenantKey);
}
