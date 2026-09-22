package com.weaver.seconddev.action;

import com.weaver.common.base.entity.result.WeaResult;
import com.weaver.common.hrm.util.Util;
import com.weaver.esb.api.rpc.EsbServerlessRpcRemoteInterface;
import com.weaver.framework.rpc.context.impl.TenantRpcContext;
import com.weaver.verupgrade.soa.workflow.request.RequestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import java.util.Map;

public abstract class WorkflowAction implements EsbServerlessRpcRemoteInterface {
    private static final Logger LOG = LoggerFactory.getLogger(WorkflowAction.class);

    @Resource
    private RequestInfo requestInfoImpl;

    @Override
    public WeaResult<Map<String, Object>> execute(Map<String, Object> params) {
        if (params == null || params.get("requestId") == null) {
            return WeaResult.fail("缺少必要参数 requestId");
        }

        Long userId = TenantRpcContext.getEmployeeIdLong();
        Long requestId = Util.getLongValue(params.get("requestId"));
        try {
            return doExecute(requestInfoImpl.getRequestInfo(requestId, userId));
        } catch (Exception e) {
            LOG.error("Workflow action execution failed, requestId={}", requestId, e);
            return WeaResult.fail("流程处理失败");
        }
    }

    public abstract WeaResult<Map<String, Object>> doExecute(RequestInfo requestInfo);
}
