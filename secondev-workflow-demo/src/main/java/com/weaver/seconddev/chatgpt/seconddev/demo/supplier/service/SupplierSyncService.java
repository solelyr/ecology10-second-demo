package com.weaver.seconddev.chatgpt.seconddev.demo.supplier.service;

import com.weaver.common.hrm.util.HrmContextUtil;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.client.VendorClient;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.mapper.SupplierMapper;
import com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.action.SupplierSubmission;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.request.AddVendorRequest;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.response.VendorCreated;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.service.callback.SupplierSuccessCallback;

import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @USER: solelyr
 * @DATE: 2026/11/06 09:53
 * @DESCRIPTION: 在最终审批提交前同步新增第三方供应商并回写 E10
 */
@Service("solelyrSupplierSyncService")
public final class SupplierSyncService {
    /** 是否启用实际供应商推送。 */
    private static final boolean ENABLED = true;
    /** 第三方供应商新增接口的账套编码。 */
    private static final String ACC_ID = "ACC_ID";

    private final boolean enabled;
    private final String accId;
    private final com.weaver.seconddev.chatgpt.seconddev.demo.supplier.service.SupplierFormReader formReader;
    private final com.weaver.seconddev.chatgpt.seconddev.demo.supplier.client.VendorClient client;
    private final SupplierSuccessCallback callback;
    private final Function<SupplierSubmission, AddVendorRequest> mapper;

    /**
     * 使用正式字段映射创建供应商同步服务。
     *
     * @param client 第三方供应商接口客户端
     * @param callback 供应商新增成功后的回写组件
     * @param formReader 按流程 ID 查询 E10 表单数据的组件
     */
    @Autowired
    public SupplierSyncService(com.weaver.seconddev.chatgpt.seconddev.demo.supplier.client.VendorClient client, SupplierSuccessCallback callback, com.weaver.seconddev.chatgpt.seconddev.demo.supplier.service.SupplierFormReader formReader) {
        this(client, callback, formReader, ENABLED, ACC_ID, new SupplierMapper()::map);
    }

    /**
     * 使用指定映射创建供应商同步服务。离线测试通过此构造方法提供含合成发展日期的映射。
     *
     * @param client 第三方供应商接口客户端
     * @param callback 供应商新增成功后的回写组件
     * @param formReader 按流程 ID 查询 E10 表单数据的组件
     * @param enabled 是否启用供应商推送
     * @param accId 第三方供应商新增接口的账套编码
     * @param mapper 将 E10 动作输入转换为第三方请求的映射函数
     */
    SupplierSyncService(VendorClient client, SupplierSuccessCallback callback, SupplierFormReader formReader, boolean enabled,
                        String accId, Function<SupplierSubmission, AddVendorRequest> mapper) {
        this.enabled = enabled;
        this.accId = accId;
        this.formReader = formReader;
        this.client = client;
        this.callback = callback;
        this.mapper = mapper;
    }

    /**
     * 按流程 ID 读取已保存的 E10 表单，再同步新增供应商并回写。
     *
     * @param requestId E10 流程实例 ID
     * @param employeeId 当前 E10 操作用户 ID
     * @param tenantKey 当前租户标识
     * @return 成功新增并完成回写后的供应商结果
     */
    public VendorCreated synchronize(long requestId, Long employeeId, String tenantKey) {
        String operator = employeeId == null ? null : employeeId.toString();
        validateContext(operator, tenantKey);
        if (employeeId <= 0 || requestId <= 0) {
            throw new com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException(com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException.Kind.VALIDATION, "流程 ID 和操作用户 ID 必须为正整数");
        }
        SupplierSubmission input = formReader.read(requestId, HrmContextUtil.build(employeeId, tenantKey));
        input.setAccId(accId);
        return push(input, operator, tenantKey);
    }

    /**
     * 在审批提交前同步新增供应商并回写 E10。失败时将异常交给 Action 处理。
     *
     * @param input 已准备好的表单数据及上下文
     * @param employeeId E10 操作用户 ID
     * @param tenantKey 当前租户标识
     * @return 成功新增并完成回写后的供应商结果
     */
    VendorCreated synchronize(SupplierSubmission input, String employeeId, String tenantKey) {
        validateContext(employeeId, tenantKey);
        return push(input, employeeId, tenantKey);
    }

    /**
     * 校验服务开关以及 E10 用户和租户上下文。
     *
     * @param employeeId E10 操作用户 ID
     * @param tenantKey 当前租户标识
     */
    private void validateContext(String employeeId, String tenantKey) {
        if (!enabled) {
            throw new com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException(com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException.Kind.CONFIGURATION, "供应商 demo 尚未启用");
        }
        if (employeeId == null || employeeId.trim().isEmpty() || tenantKey == null || tenantKey.trim().isEmpty()) {
            throw new com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException(IntegrationException.Kind.VALIDATION, "缺少受信操作用户或租户");
        }
    }

    /**
     * 校验映射、调用第三方新增接口并执行 E10 回写。
     *
     * @param input 已准备好的表单数据及上下文
     * @param employeeId E10 操作用户 ID
     * @param tenantKey 当前租户标识
     * @return 成功新增并完成回写后的供应商结果
     */
    private VendorCreated push(SupplierSubmission input, String employeeId, String tenantKey) {
        AddVendorRequest request = mapper.apply(input);
        request.setToken(client.fetchToken());
        VendorCreated created = client.add(request);
        callback.afterSuccess(input, created, employeeId, tenantKey);
        return created;
    }
}
