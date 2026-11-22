package com.weaver.seconddev.solelyr.workflow.action;

import com.weaver.seconddev.solelyr.workflow.exception.WorkflowErrors;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.weaver.common.base.entity.result.WeaResult;
import com.weaver.esb.api.rpc.EsbServerlessRpcRemoteInterface;
import com.weaver.framework.rpc.context.impl.TenantRpcContext;
import com.weaver.seconddev.solelyr.workflow.exception.WorkflowEsbException;
import com.weaver.seconddev.solelyr.workflow.exception.WorkflowEsbExceptionHandler;
import com.weaver.seconddev.solelyr.workflow.model.WorkflowEsbForm;
import com.weaver.seconddev.solelyr.workflow.service.WorkflowFormQueryService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * @USER: solelyr
 * @DATE: 2026/11/11 15:10
 * @DESCRIPTION: 动作流 Action：按流程 requestId 返回字段信息和以数据库列名为键的表单数据
 */
@Service("solelyrWorkflowEsbAction")
public class WorkflowEsbAction implements EsbServerlessRpcRemoteInterface {
    private final WorkflowFormQueryService service;
    private final Map<String, WorkflowEsbActionHandler<?>> handlers;

    public WorkflowEsbAction(WorkflowFormQueryService service,
                                   Map<String, WorkflowEsbActionHandler<?>> handlers) {
        this.service = service;
        this.handlers = handlers;
    }

    /**
     * 执行查询。参数 requestId（兼容 requestid）建议以字符串传入以保留长整数精度。
     *
     * @param params 动作参数
     * @return 默认在 data 中返回表单 Map，指定 serviceName 时直接在 data 中返回处理结果
     */
    @Override
    public WeaResult<Map<String, Object>> execute(Map<String, Object> params) {
        try {
            long requestId = requestId(params);
            WorkflowEsbActionHandler<?> handler = resolveHandler(params.get("serviceName"));
            WorkflowEsbForm result = service.query(requestId, currentEmployeeId(), currentTenantKey());
            result.setParams(new LinkedHashMap<>(params));
            if (handler == null) {
                return WeaResult.success(result.toMap());
            }
            return WeaResult.success(toResponseMap(handler.execute(result)));
        } catch (RuntimeException failure) {
            return WorkflowEsbExceptionHandler.failureResponse("Workflow ESB action failed", failure,
                    "工作流 ESB 执行失败，请核对服务端日志");
        }
    }

    private static Map<String, Object> toResponseMap(Object data) {
        if (data == null) {
            return null;
        }
        Object json = JSON.toJSON(data);
        if (json instanceof JSONObject) {
            return (JSONObject) json;
        }
        throw new WorkflowEsbException(WorkflowErrors.RESULT_INVALID,
                "execute 的返回值必须能转换为 JSON 对象");
    }

    private WorkflowEsbActionHandler<?> resolveHandler(Object serviceName) {
        if (serviceName == null) {
            return null;
        }
        if (!(serviceName instanceof String) || ((String) serviceName).trim().isEmpty()) {
            throw new WorkflowEsbException(WorkflowErrors.HANDLER_INVALID,
                    "serviceName 必须是非空字符串");
        }
        WorkflowEsbActionHandler<?> handler = handlers.get(((String) serviceName).trim());
        if (handler == null) {
            throw new WorkflowEsbException(WorkflowErrors.HANDLER_INVALID,
                    "serviceName 对应的表单处理策略不存在");
        }
        return handler;
    }

    /**
     * 当前 RPC 上下文中的操作用户 ID。
     *
     * @return 用户 ID
     */
    protected Long currentEmployeeId() {
        return TenantRpcContext.getEmployeeIdLong();
    }

    /**
     * 当前 RPC 上下文中的租户标识。
     *
     * @return 租户标识
     */
    protected String currentTenantKey() {
        return TenantRpcContext.getCurrentTenantKey();
    }

    /**
     * 从参数中提取正整数 requestId。
     *
     * @param params 动作参数
     * @return 流程实例 ID
     */
    static long requestId(Map<String, Object> params) {
        Object value = params == null ? null : params.get("requestId");
        if (value == null && params != null) {
            value = params.get("requestid");
        }
        if (!(value instanceof String) && !(value instanceof Number)) {
            throw new WorkflowEsbException(WorkflowErrors.REQUEST_ID_INVALID, "动作参数缺少 requestId");
        }
        String text = String.valueOf(value).trim();
        if (!text.matches("[1-9][0-9]{0,18}")) {
            throw new WorkflowEsbException(WorkflowErrors.REQUEST_ID_INVALID, "requestId 必须是正整数");
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new WorkflowEsbException(WorkflowErrors.REQUEST_ID_INVALID, "requestId 超出范围", e);
        }
    }
}
