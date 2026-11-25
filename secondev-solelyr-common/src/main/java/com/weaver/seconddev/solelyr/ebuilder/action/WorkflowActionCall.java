package com.weaver.seconddev.solelyr.ebuilder.action;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.TypeReference;
import com.weaver.common.base.entity.result.WeaResult;
import com.weaver.common.base.enumeration.result.WeaResultCodeEnum;
import com.weaver.common.hrm.util.Util;
import com.weaver.ebuilder.form.client.entity.data.*;
import com.weaver.ebuilder.form.client.entity.field.enums.FieldOperationTypeEnum;
import com.weaver.ebuilder.form.client.service.data.RemoteSimpleDataService;
import com.weaver.esb.api.rpc.EsbServerlessRpcRemoteInterface;
import com.weaver.framework.rpc.context.impl.TenantRpcContext;
import com.weaver.publishkit.api.util.PublishKitRuntimeUtil;
import com.weaver.seconddev.solelyr.workflow.action.WorkflowEsbActionHandler;
import com.weaver.seconddev.solelyr.workflow.exception.WorkflowErrors;
import com.weaver.seconddev.solelyr.workflow.exception.WorkflowEsbException;
import com.weaver.seconddev.solelyr.workflow.model.WorkflowEsbForm;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @USER: solelyr
 * @DATE: 2026/11/19 17:52
 * @DESCRIPTION: 针对外部系统使用WorkflowEsbAction的开发环境测试工具类
 */
@Service("solelyrWorkflowActionCall")
public class WorkflowActionCall implements EsbServerlessRpcRemoteInterface {
    private static final Logger LOG = LoggerFactory.getLogger(WorkflowActionCall.class);
    private final Map<String, WorkflowEsbActionHandler<?>> handlers;
    private static final String FORM_PATH = "/api/secondev/workflow/esb/form";
    private static final RequestConfig HTTP_CONFIG = RequestConfig.custom()
            .setConnectTimeout(10000)
            .setSocketTimeout(10000)
            .build();
    private static final TypeReference<WeaResult<WorkflowEsbForm>> FORM_RESULT = new TypeReference<WeaResult<WorkflowEsbForm>>() {};

    public WorkflowActionCall(Map<String, WorkflowEsbActionHandler<?>> handlers) {
        this.handlers = handlers;
    }

    @Override
    public WeaResult<Map<String, Object>> execute(Map<String, Object> params) {

        String e10url = Util.null2String(params.get("e10url"));
        String cookie = Util.null2String(params.get("cookie"));
        long requestId = Util.getLongValue(params.get("requestid"));
        String name = Util.null2String(params.get("sname"));
        String formId = Util.null2String(params.get("formid"));
        String dataId = Util.null2String(params.get("dataid"));
        String appId = Util.null2String(params.get("appid"));
        Long employeeId = TenantRpcContext.getEmployeeIdLong();
        String tenantKey = TenantRpcContext.getCurrentTenantKey();

        try {
            WorkflowEsbActionHandler<?> handler = handlers.get(name);
            if (handler == null) {
                throw new WorkflowEsbException(WorkflowErrors.HANDLER_INVALID, "serverName 对应的工作流处理器不存在");
            }
            URI target = origin(e10url);
            if (cookie.chars().anyMatch(value -> value < 32 || value == 127)) {
                throw new WorkflowEsbException(WorkflowErrors.FORM_COOKIE_INVALID, "cookie 包含无效字符");
            }

            WorkflowEsbForm form = query(target, cookie, requestId);

            form.setEmployeeId(employeeId);
            form.setTenantKey(tenantKey);
            Map<String, Object> requestParams = new LinkedHashMap<>();
            if (form.getParams() != null) {
                requestParams.putAll(form.getParams());
            }
            requestParams.put("requestId", Long.toString(requestId));
            requestParams.put("serviceName", name);
            form.setParams(requestParams);
            Object result = handler.execute(form);
            update(appId, JSON.toJSONString(result), formId, dataId, Util.null2String(employeeId), tenantKey);
            return WeaResult.success();
        } catch (RuntimeException failure) {
            LOG.error("Workflow action call failed: requestId={}, serviceName={}, dataId={}", requestId, name, dataId, failure);
            recordFailure(failure, appId, formId, dataId, Util.null2String(employeeId), tenantKey);
            return WeaResult.fail(WeaResultCodeEnum.ERROR.getCode(),
                    failure.getMessage(), true);
        }
    }

    /**
     * 回写失败说明，回写故障作为附加异常保留，不遮蔽首个错误。
     *
     * @param failure 首个业务或运行异常
     * @param appId 目标应用 ID
     * @param formId 目标表单对象 ID
     * @param dataId 目标数据记录 ID
     * @param userId 当前操作人 ID
     * @param tenantKey 当前租户标识
     */
    private void recordFailure(RuntimeException failure, String appId, String formId, String dataId,
                               String userId, String tenantKey) {
        try {
            update(appId, failure.getMessage(), formId, dataId, userId, tenantKey);
        } catch (RuntimeException writeFailure) {
            if (writeFailure != failure) {
                failure.addSuppressed(writeFailure);
                LOG.error("Workflow action result write failed: formId={}, dataId={}", formId, dataId, writeFailure);
            }
        }
    }

    private static URI origin(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            int port = uri.getPort();
            if ((!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))
                    || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || port == 0 || port > 65535
                    || (!uri.getRawPath().isEmpty() && !"/".equals(uri.getRawPath()))) {
                throw new WorkflowEsbException(WorkflowErrors.FORM_ENDPOINT_INVALID, "e10Url 必须是 HTTP 或 HTTPS 的 E10 系统地址");
            }
            if (("http".equalsIgnoreCase(scheme) && port == 80) || ("https".equalsIgnoreCase(scheme) && port == 443)) {
                port = -1;
            }
            return new URI(scheme.toLowerCase(Locale.ROOT), null, uri.getHost().toLowerCase(Locale.ROOT),
                    port, null, null, null);
        } catch (URISyntaxException failure) {
            throw new WorkflowEsbException(WorkflowErrors.FORM_ENDPOINT_INVALID, "e10Url 格式无效");
        }
    }

    private static WorkflowEsbForm query(URI target, String cookie, long requestId) {
        HttpGet get = new HttpGet(target.resolve(FORM_PATH + "?requestid=" + requestId));
        get.setHeader("Cookie", cookie);
        get.setHeader("Accept", "application/json");
        try (CloseableHttpClient http = HttpClients.custom()
                .setDefaultRequestConfig(HTTP_CONFIG)
                .disableRedirectHandling()
                .disableCookieManagement()
                .disableAutomaticRetries()
                .build();
             CloseableHttpResponse response = http.execute(get)) {
            int status = response.getStatusLine().getStatusCode();
            if (status != 200 || response.getEntity() == null) {
                throw new WorkflowEsbException(WorkflowErrors.FORM_QUERY_FAILED,
                        "E10 表单接口查询失败，HTTP " + status);
            }
            WeaResult<WorkflowEsbForm> result = JSON.parseObject(
                    EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8), FORM_RESULT);
            if (result == null || result.getCode() != WeaResultCodeEnum.SUCCESS.getCode()
                    || !result.isStatus() || result.isFail()) {
                throw new WorkflowEsbException(WorkflowErrors.FORM_QUERY_FAILED,
                        "E10 未能返回流程表单，请检查登录状态和流程访问权限");
            }
            WorkflowEsbForm form = result.getData();
            if (form == null || form.getRequestId() != requestId || form.getEmployeeId() == null
                    || form.getEmployeeId() <= 0 || form.getTenantKey() == null || form.getTenantKey().trim().isEmpty()) {
                throw new WorkflowEsbException(WorkflowErrors.FORM_RESPONSE_INVALID,
                        "E10 返回的流程实例或登录信息不完整");
            }
            return form;
        } catch (JSONException failure) {
            throw new WorkflowEsbException(WorkflowErrors.FORM_RESPONSE_INVALID, "E10 表单接口响应无法解析", failure);
        } catch (IOException failure) {
            throw new WorkflowEsbException(WorkflowErrors.FORM_QUERY_FAILED, "请求 E10 表单接口失败", failure);
        }
    }


    @Autowired
    private PublishKitRuntimeUtil publishKitRuntimeUtil;
    private void update(String appId, String result, String formId, String dataId, String userId, String tenantKey) {
        RemoteSimpleDataService remoteSimpleDataService = publishKitRuntimeUtil.buildRpcService(RemoteSimpleDataService.class, "ebuilderform", appId);

        /**
         * 直接通过id更新数据， 即update table set a = '11' where id = 1
         */
        EBDataChangeReqDto ebDataChangeReqDto = new EBDataChangeReqDto();
        // 构建基础参数; objId 表单id, operator 操作人, tenantKey 租户
        ebDataChangeReqDto.setHeader(new EBDataReqHeader(formId, userId, tenantKey));
        // 请求操作信息
        EBDataReqOperation operation = new EBDataReqOperation();
        operation.setUpdateType(EBDataUpdateType.ids);
        // 若数据写入后就要从前台看到数据或者立即操作本次写入的数据, 需要调整后置处理为同步 权限处理完成再返回
        // 默认asyncPostProcess为true， 异步处理后置权限、编码、按钮动作等逻辑， 如有同步需要可按下方参数设置
        operation.setAsyncPostProcess(false);
        operation.setReqType(FieldOperationTypeEnum.dataKey);
        ebDataChangeReqDto.setOperation(operation);

        // 数据组装
        List<EBDataReqDto> datas = new ArrayList<>();
        EBDataReqDto ebDataReqDto1 = new EBDataReqDto();
        List<EBDataReqDetailDto> mainData1 = new ArrayList<>();
        // 设置更新id
        mainData1.add(field("id", dataId));
        mainData1.add(field("fhjg", result));
        ebDataReqDto1.setMainDatas(mainData1);
        datas.add(ebDataReqDto1);

        ebDataChangeReqDto.setDatas(datas);
        EBDataChangeResult written = remoteSimpleDataService.updateFormData(ebDataChangeReqDto);
        if (written == null || !Boolean.TRUE.equals(written.getStatus())) {
            throw new WorkflowEsbException(WorkflowErrors.RESULT_INVALID, "OA 测试表单结果回写未获确认");
        }

    }

    private EBDataReqDetailDto field(String key, String value) {
        EBDataReqDetailDto field = new EBDataReqDetailDto();
        field.setDataKey(key);
        field.setContent(value);
        return field;
    }
}
