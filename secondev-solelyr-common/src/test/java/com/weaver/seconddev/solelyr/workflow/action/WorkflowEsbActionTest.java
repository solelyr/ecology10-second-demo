package com.weaver.seconddev.solelyr.workflow.action;

import static com.weaver.seconddev.solelyr.workflow.TestSupport.check;
import static com.weaver.seconddev.solelyr.workflow.TestSupport.eq;

import com.weaver.common.base.entity.result.WeaResult;
import com.weaver.seconddev.solelyr.workflow.TestSupport;
import com.weaver.seconddev.solelyr.workflow.exception.WorkflowErrors;
import com.weaver.seconddev.solelyr.workflow.exception.WorkflowEsbException;
import com.weaver.seconddev.solelyr.workflow.model.WorkflowEsbForm;
import com.weaver.seconddev.solelyr.workflow.service.WorkflowFormQueryService;
import java.util.HashMap;
import java.util.Collections;
import java.util.Map;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * @USER: solelyr
 * @DATE: 2026/11/11 15:10
 * @DESCRIPTION: 验证 Action 参数解析、WeaResult 成功/失败结构及 Spring 装配
 */
public final class WorkflowEsbActionTest {
    /**
     * 测试入口类，禁止实例化。
     */
    private WorkflowEsbActionTest() {
    }

    /**
     * 运行全部用例。
     *
     * @param args 未使用
     */
    public static void main(String[] args) {
        check("字符串 requestId 成功返回 data", () -> {
            WeaResult<Map<String, Object>> r = action(new StubService(null)).execute(params("requestId", "101"));
            eq(true, r.isStatus());
            eq("101", r.getData().get("requestId"));
        });
        check("兼容旧键 requestid 与数字类型", () -> {
            WeaResult<Map<String, Object>> r = action(new StubService(null)).execute(params("requestid", 102L));
            eq(true, r.isStatus());
            eq("102", r.getData().get("requestId"));
        });
        check("缺少或非法 requestId 返回失败结构", () -> {
            eq(false, action(new StubService(null)).execute(new HashMap<String, Object>()).isStatus());
            eq(false, action(new StubService(null)).execute(null).isStatus());
            WeaResult<Map<String, Object>> r = action(new StubService(null)).execute(params("requestId", "-1"));
            eq(false, r.isStatus());
            eq("requestId 必须是正整数", r.getMsg());
            eq(false, action(new StubService(null)).execute(params("requestId", "99999999999999999999")).isStatus());
        });
        check("服务异常转换为 WeaResult 失败", () -> {
            WeaResult<Map<String, Object>> r = action(new StubService(new WorkflowEsbException(
                    WorkflowErrors.REQUEST_NOT_FOUND, "未找到流程实例：101"))).execute(params("requestId", "101"));
            eq(false, r.isStatus());
            eq("未找到流程实例：101", r.getMsg());
            WeaResult<Map<String, Object>> unknown = action(new StubService(new IllegalStateException("x")))
                    .execute(params("requestId", "101"));
            eq(false, unknown.isStatus());
            eq("工作流 ESB 执行失败，请核对服务端日志", unknown.getMsg());
        });
        check("Spring 可装配 Service 与 Action", () -> {
            DefaultListableBeanFactory factory = beanFactory();
            factory.registerSingleton("formTableService", TestSupport.tableService(null));
            factory.registerSingleton("formTableFieldService", TestSupport.columnService(null));
            factory.registerBeanDefinition("solelyrWorkflowFormQueryService",
                    new RootBeanDefinition(WorkflowFormQueryService.class));
            factory.registerBeanDefinition("solelyrWorkflowEsbAction",
                    new RootBeanDefinition(DefaultWorkflowEsbAction.class));
            factory.registerBeanDefinition("workflowEsbAction",
                    new RootBeanDefinition(WorkflowEsbAction.class));
            eq(WorkflowEsbAction.class, factory.getBean("workflowEsbAction").getClass());
            eq(true, factory.getBean("solelyrWorkflowEsbAction") instanceof WorkflowEsbAction);
            java.lang.reflect.Field injected = WorkflowFormQueryService.class.getDeclaredField("formTableFieldService");
            injected.setAccessible(true);
            eq(true, injected.get(factory.getBean("solelyrWorkflowFormQueryService")) != null);
        });
        check("Spring 按策略 Bean 名称分发并返回自定义对象", () -> {
            DefaultListableBeanFactory factory = beanFactory();
            StubService service = new StubService(null);
            factory.registerSingleton("otherService", service);
            factory.registerSingleton("customAction", (WorkflowEsbActionHandler<QueryResponse>)
                    result -> new QueryResponse(String.valueOf(result.getRequestId())));
            factory.registerSingleton("paramsAction", (WorkflowEsbActionHandler<Map<String, Object>>)
                    WorkflowEsbForm::getParams);
            factory.registerSingleton("listAction", (WorkflowEsbActionHandler<Object>)
                    result -> Collections.singletonList(result.getRequestId()));
            factory.registerSingleton("failedAction", (WorkflowEsbActionHandler<Object>) result -> {
                throw new WorkflowEsbException(() -> "SUPPLIER.PUSH_FAILED", "供应商接口调用失败", new IllegalStateException("内部错误"));
            });
            factory.registerBeanDefinition("workflowEsbAction", new RootBeanDefinition(TestAction.class));
            WorkflowEsbAction entry = factory.getBean("workflowEsbAction", WorkflowEsbAction.class);

            Map<String, Object> request = params("requestId", "101");
            request.put("serviceName", "workflowEsbAction");
            eq("serviceName 对应的表单处理策略不存在", entry.execute(request).getMsg());
            request.put("serviceName", "customAction");
            WeaResult<Map<String, Object>> response = entry.execute(request);
            eq(true, response.isStatus());
            eq(Collections.singletonMap("requestId", "101"), response.getData());

            request.put("serviceName", "paramsAction");
            request.put("customParam", Collections.singletonMap("enabled", true));
            WeaResult<Map<String, Object>> paramsResponse = entry.execute(request);
            eq(true, paramsResponse.isStatus());
            eq(request, paramsResponse.getData());

            request.put("serviceName", "failedAction");
            WeaResult<Map<String, Object>> failure = entry.execute(request);
            eq(false, failure.isStatus());
            eq("供应商接口调用失败", failure.getMsg());

            request.put("serviceName", "listAction");
            eq("execute 的返回值必须能转换为 JSON 对象", entry.execute(request).getMsg());
            request.put("serviceName", "otherService");
            eq("serviceName 对应的表单处理策略不存在", entry.execute(request).getMsg());
            request.put("serviceName", "missingAction");
            eq("serviceName 对应的表单处理策略不存在", entry.execute(request).getMsg());
            request.put("serviceName", " ");
            eq("serviceName 必须是非空字符串", entry.execute(request).getMsg());
        });
        TestSupport.finish("WorkflowEsbActionTest");
    }

    /**
     * 构造只含一个键值的动作参数。
     *
     * @param key 参数名
     * @param value 参数值
     * @return 动作参数
     */
    private static Map<String, Object> params(String key, Object value) {
        Map<String, Object> params = new HashMap<>();
        params.put(key, value);
        return params;
    }

    /**
     * 创建使用固定用户和租户上下文的 Action，避免依赖真实 RPC 上下文。
     *
     * @param service 被 Action 调用的查询服务
     * @return 测试用 Action
     */
    private static WorkflowEsbAction action(WorkflowFormQueryService service) {
        return new TestAction(service, Collections.emptyMap());
    }

    private static final class TestAction extends WorkflowEsbAction {
        TestAction(WorkflowFormQueryService service, Map<String, WorkflowEsbActionHandler<?>> handlers) {
            super(service, handlers);
        }

        @Override
        protected Long currentEmployeeId() {
            return 9L;
        }

        @Override
        protected String currentTenantKey() {
            return "t1";
        }
    }

    private static DefaultListableBeanFactory beanFactory() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        AutowiredAnnotationBeanPostProcessor processor = new AutowiredAnnotationBeanPostProcessor();
        processor.setBeanFactory(factory);
        factory.addBeanPostProcessor(processor);
        return factory;
    }

    public static final class QueryResponse {
        private final String requestId;

        QueryResponse(String requestId) {
            this.requestId = requestId;
        }

        public String getRequestId() {
            return requestId;
        }
    }

    /** 返回固定结果或抛出指定异常的服务替身。 */
    private static final class StubService extends WorkflowFormQueryService {
        private final RuntimeException error;

        /**
         * 创建服务替身。
         *
         * @param error 查询时抛出的异常；为 null 时返回固定结果
         */
        StubService(RuntimeException error) {
            this.error = error;
        }

        /**
         * 校验 Action 传入的用户上下文，并返回固定结果或抛出预设异常。
         *
         * @param requestId 流程实例 ID
         * @param employeeId 操作用户 ID
         * @param tenantKey 租户标识
         * @return 只含 requestId 的查询结果
         */
        @Override
        public WorkflowEsbForm query(long requestId, Long employeeId, String tenantKey) {
            if (error != null) {
                throw error;
            }
            eq(9L, employeeId);
            eq("t1", tenantKey);
            WorkflowEsbForm result = new WorkflowEsbForm();
            result.setRequestId(requestId);
            return result;
        }
    }
}
