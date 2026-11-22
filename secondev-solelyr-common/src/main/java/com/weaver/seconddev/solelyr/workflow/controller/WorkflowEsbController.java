package com.weaver.seconddev.solelyr.workflow.controller;

import com.weaver.seconddev.solelyr.workflow.exception.WorkflowErrors;
import com.weaver.common.authority.annotation.WeaPermission;
import com.weaver.common.base.entity.result.WeaResult;
import com.weaver.common.base.enumeration.result.WeaResultCodeEnum;
import com.weaver.framework.rpc.context.impl.TenantRpcContext;
import com.weaver.seconddev.solelyr.workflow.exception.WorkflowEsbException;
import com.weaver.seconddev.solelyr.workflow.mapper.WorkflowEsbFormMapper;
import com.weaver.seconddev.solelyr.workflow.model.WorkflowEsbForm;
import com.weaver.seconddev.solelyr.workflow.service.WorkflowFormQueryService;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @USER: solelyr
 * @DATE: 2026/11/14 23:45
 * @DESCRIPTION: 提供工作流表单查询接口并支持按 Mapper 转换返回实体
 */
@RestController
@RequestMapping("/api/secondev/workflow/esb")
@WeaPermission(publicPermission = true)
public class WorkflowEsbController {
    private static final Logger LOG = LoggerFactory.getLogger(WorkflowEsbController.class);
    private final WorkflowFormQueryService service;
    private final Map<String, WorkflowEsbFormMapper<?>> mappers;

    public WorkflowEsbController(WorkflowFormQueryService service, Map<String, WorkflowEsbFormMapper<?>> mappers) {
        this.service = service;
        this.mappers = mappers;
    }

    @GetMapping("/form")
    public WeaResult<WorkflowEsbForm> getForm(@RequestParam(value = "requestid", required = false) String requestId) {
        return respond(() -> query(requestId));
    }

    @GetMapping("/form/mapped")
    public WeaResult<Object> getMappedForm(@RequestParam(value = "requestid", required = false) String requestId,
                                          @RequestParam(value = "mapper", required = false) String mapperName) {
        return respond(() -> resolveMapper(mapperName).map(query(requestId)));
    }

    private WorkflowEsbForm query(String requestId) {
        if (requestId == null || !requestId.trim().matches("[1-9][0-9]{0,18}")) {
            throw new WorkflowEsbException(WorkflowErrors.REQUEST_ID_INVALID, "requestid 必须是正整数");
        }
        long id;
        try {
            id = Long.parseLong(requestId.trim());
        } catch (NumberFormatException e) {
            throw new WorkflowEsbException(WorkflowErrors.REQUEST_ID_INVALID, "requestid 超出范围", e);
        }
        return service.query(id, TenantRpcContext.getEmployeeIdLong(), TenantRpcContext.getCurrentTenantKey());
    }

    private WorkflowEsbFormMapper<?> resolveMapper(String mapperName) {
        if (mapperName == null || mapperName.trim().isEmpty()) {
            throw new WorkflowEsbException(WorkflowErrors.MAPPER_INVALID, "mapper 不能为空");
        }
        String name = mapperName.trim();
        WorkflowEsbFormMapper<?> match = mappers.get(name);
        if (match != null) {
            return match;
        }
        for (WorkflowEsbFormMapper<?> mapper : mappers.values()) {
            Class<?> type = AopUtils.getTargetClass(mapper);
            if (name.equals(type.getName()) || name.equals(type.getSimpleName())) {
                if (match != null) {
                    throw new WorkflowEsbException(WorkflowErrors.MAPPER_INVALID,
                            "mapper 对应多个 Bean，请使用 Spring Bean 名称");
                }
                match = mapper;
            }
        }
        if (match == null) {
            throw new WorkflowEsbException(WorkflowErrors.MAPPER_NOT_FOUND, "mapper 对应的映射器不存在");
        }
        return match;
    }

    private <T> WeaResult<T> respond(Supplier<T> operation) {
        try {
            return WeaResult.success(operation.get());
        } catch (WorkflowEsbException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            LOG.error("Workflow ESB request failed", failure);
            return WeaResult.fail(WeaResultCodeEnum.ERROR.getCode(),
                    "工作流 ESB 执行失败，请核对服务端日志", true);
        }
    }
}
