package com.weaver.seconddev.chatgpt.seconddev.demo.supplier.action;

import com.weaver.common.base.entity.result.WeaResult;
import com.weaver.common.base.enumeration.result.WeaResultCodeEnum;
import com.weaver.esb.api.rpc.EsbServerlessRpcRemoteInterface;
import com.weaver.framework.rpc.context.impl.TenantRpcContext;
import com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.response.VendorCreated;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.service.SupplierSyncService;

import java.util.Collections;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * @USER: solelyr
 * @DATE: 2026/11/06 09:53
 * @DESCRIPTION: 提供最终审批提交前的同步 Action，统一返回成功或阻断结果
 */
@Service("solelyrSupplierCreateAction")
public class SupplierCreateAction implements EsbServerlessRpcRemoteInterface {
    private static final Logger LOG = LoggerFactory.getLogger(SupplierCreateAction.class);
    private final SupplierSyncService service;

    /**
     * 创建供应商新增流程动作。
     *
     * @param service 供应商同步服务
     */
    public SupplierCreateAction(SupplierSyncService service) {
        this.service = service;
    }

    /**
     * 在最终审批提交前同步执行供应商新增。失败结果交由流程动作链处理。
     *
     * @param params 包含当前流程 requestId 的 E10 动作参数
     * @return 包含供应商编码的成功结果，或包含错误信息的失败结果
     */
    @Override
    public WeaResult<Map<String, Object>> execute(Map<String, Object> params) {
        try {
            long requestId = extractRequestId(params);
            Long employeeId = TenantRpcContext.getEmployeeIdLong();
            String tenantKey = TenantRpcContext.getCurrentTenantKey();
            VendorCreated created = service.synchronize(requestId, employeeId, tenantKey);
            return WeaResult.success(Collections.<String, Object>singletonMap("vendorCode", created.getVendorCode()));
        } catch (com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException e) {
            LOG.warn("Supplier Action blocked; category={}", e.getKind());
            return blocked(e.getMessage());
        } catch (RuntimeException e) {
            LOG.error("Supplier Action failed; exceptionType={}", e.getClass().getSimpleName());
            return blocked("供应商同步动作执行失败，请核对服务端日志");
        }
    }

    /**
     * 从动作参数提取 requestId，兼容旧配置中的 requestid 键。
     *
     * @param params 动作输入
     * @return 可传给 E10 流程 RPC 的正整数 ID
     */
    private long extractRequestId(Map<String, Object> params) {
        Object value = params == null ? null : params.get("requestId");
        Object legacyValue = params == null ? null : params.get("requestid");
        if (value == null && legacyValue == null) {
            throw new com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException(com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException.Kind.VALIDATION, "动作参数缺少 requestId");
        }
        long id = positiveId(value == null ? legacyValue : value);
        if (value != null && legacyValue != null && positiveId(legacyValue) != id) {
            throw new com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException(com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException.Kind.VALIDATION, "requestid 与 requestId 不一致");
        }
        return id;
    }

    /**
     * 将字符串或整数形式的流程 ID 转为 long。
     *
     * @param value 流程 ID 输入值
     * @return 正整数流程 ID
     */
    private long positiveId(Object value) {
        if (!(value instanceof String) && !(value instanceof Number)) {
            throw new com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException(com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException.Kind.VALIDATION, "动作参数 requestId 应为字符串或整数");
        }
        String text = String.valueOf(value).trim();
        if (text.matches("[0-9]+")) {
            try {
                long id = Long.parseLong(text);
                if (id > 0) {
                    return id;
                }
            } catch (NumberFormatException e) {
                throw new com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException(com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException.Kind.VALIDATION, "requestid 超出 E10 流程 ID 范围");
            }
        }
        throw new com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException(IntegrationException.Kind.VALIDATION, "requestid 必须是正整数");
    }

    /**
     * 构造阻止审批提交的平台失败结果。
     *
     * @param message 返回给流程的失败说明
     * @return 平台标准失败结果
     */
    private WeaResult<Map<String, Object>> blocked(String message) {
        return WeaResult.fail(WeaResultCodeEnum.ERROR.getCode(), message, true);
    }
}
