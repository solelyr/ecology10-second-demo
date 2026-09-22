package com.weaver.seconddev.seconddev.action;

import com.weaver.common.base.entity.result.WeaResult;
import com.weaver.common.hrm.util.Util;
import com.weaver.esb.api.rpc.EsbServerlessRpcRemoteInterface;
import com.weaver.framework.rpc.context.impl.TenantRpcContext;
import com.weaver.verupgrade.soa.workflow.request.RequestInfo;

import javax.annotation.Resource;
import java.util.Map;

/**
 * @DESCRIPTION:
 * @USER: solelyr
 * @DATE: 2026/6/22 16:20
 */
public abstract class WorkflowAction implements EsbServerlessRpcRemoteInterface {
    @Resource
    private RequestInfo requestInfoImpl;

    @Override
    public WeaResult<Map<String, Object>> execute(Map<String, Object> params) {
        Long userId = TenantRpcContext.getEmployeeIdLong();
        String tenantKey = TenantRpcContext.getCurrentTenantKey();
        Long requestId = Util.getLongValue(params.get("requestId"));
        try {
            return doExecute(requestInfoImpl.getRequestInfo(requestId,userId));
        }catch (Exception e){
            e.printStackTrace();
            return WeaResult.fail(e.getMessage());
        }
    }

    public abstract WeaResult<Map<String, Object>> doExecute(RequestInfo requestInfo);
}
