package com.weaver.seconddev.chatgpt.seconddev.demo.supplier.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.weaver.common.base.entity.result.WeaResult;
import com.weaver.common.base.inherit.InheritThreadLocalContainer;
import com.weaver.common.form.data.FormData;
import com.weaver.common.form.data.detail.FormDataDetail;
import com.weaver.common.form.data.option.FormDataOption;
import com.weaver.common.form.metadata.field.FormField;
import com.weaver.common.form.metadata.subform.SubForm;
import com.weaver.ebuilder.form.client.entity.obj.Obj;
import com.weaver.ebuilder.form.client.service.flow.EbuilderFlowForSpecializedApp;
import com.weaver.ebuilder.form.client.service.obj.RemoteEBObjService;
import com.weaver.framework.rpc.context.impl.TenantRpcContext;
import com.weaver.framework.log4j2.container.MainThreadTagContainer;
import com.weaver.publishkit.api.util.PublishKitRuntimeUtil;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.action.SupplierCreateAction;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.client.VendorClient;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.mapper.SupplierMapper;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.request.VendorArchive;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.action.SupplierSubmission;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.request.AddVendorRequest;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.response.VendorCreated;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.service.callback.E10SupplierSuccessCallback;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.service.callback.SupplierSuccessCallback;
import com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException;
import com.weaver.seconddev.chatgpt.seconddev.integration.workflow.WorkflowFormData;
import com.weaver.seconddev.chatgpt.seconddev.integration.workflow.WorkflowFormReader;
import com.weaver.teams.domain.user.SimpleEmployee;
import com.weaver.workflow.common.constant.formdef.WfpRelateFormType;
import com.weaver.workflow.common.entity.formdef.RequestFormInfoEntity;
import com.weaver.workflow.common.entity.formdef.WfpRelateFormEntity;
import com.weaver.workflow.common.entity.rpc.WfcRequestRpcEntity;
import com.weaver.workflow.core.api.rest.publicapi.WfcFormFieldRest;
import com.weaver.workflow.core.api.rest.publicapi.WfcGetRequestDataRest;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * @USER: solelyr
 * @DATE: 2026/11/06 09:53
 * @DESCRIPTION: 离线验证显式映射、接口实体、失败传播及 E10 回写契约
 */
public final class SupplierDemoTest {
    private static int passed;
    private static final String[] FIELDS = {"gysbm","gysmc","gysjc","ssfl","bz","sh","ssyx","yxzh"};

    /**
     * 运行供应商同步的全部离线测试。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        check("Spring creates supplier beans without service properties", () -> {
            DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
            AutowiredAnnotationBeanPostProcessor autowired = new AutowiredAnnotationBeanPostProcessor();
            autowired.setBeanFactory(beans);
            beans.addBeanPostProcessor(autowired);
            beans.registerBeanDefinition("vendorClient", new RootBeanDefinition(VendorClient.class));
            beans.registerBeanDefinition("workflowFormReader", new RootBeanDefinition(WorkflowFormReader.class));
            beans.registerBeanDefinition("supplierFormReader", new RootBeanDefinition(SupplierFormReader.class));
            beans.registerBeanDefinition("supplierSyncService", new RootBeanDefinition(SupplierSyncService.class));
            SupplierSuccessCallback callback = (source, result, employee, tenant) -> {};
            beans.registerSingleton("offlineSupplierCallback", callback);
            beans.registerSingleton("offlinePublishKit", new PublishKitRuntimeUtil());
            try {
                beans.getBean(SupplierSyncService.class);
            } finally {
                beans.destroySingletons();
            }
        });
        check("exact wire casing and leading-zero codes", () -> {
            Fixture f = fixture();
            f.service.synchronize(input(), "11", "tenant-a");

            JSONObject token = JSON.parseObject(f.client.tokenBody);
            JSONObject body = JSON.parseObject(f.client.vendorBody);
            JSONObject archive = body.getJSONObject("oArchives");

            equal("fixture-app", token.getString("appid"));
            equal("fixture-secret", token.getString("secretkey"));
            equal("fixture-token", body.getString("Token"));
            equal("001", body.getString("AccId"));
            require(body.containsKey("billid") && !body.containsKey("billId") && !body.containsKey("archives"));
            require(!body.containsKey("token") && !body.containsKey("accId"));
            require(archive.containsKey("cVenExch_name") && archive.containsKey("dVenDevDate") && archive.containsKey("cVCCode"));
            require(!archive.containsKey("currencyName") && !archive.containsKey("developmentDate") && !archive.containsKey("categoryCode"));
            equal("00001234", archive.getString("cVenAccount"));
            equal(1, f.callbacks.get());
        });
        check("write-back failure propagates after one successful vendor creation", () -> {
            Fixture f = fixture();
            SupplierSuccessCallback callback = (source, result, employee, tenant) -> {
                f.callbacks.incrementAndGet();
                throw new IntegrationException(IntegrationException.Kind.WRITEBACK, "offline write-back failure");
            };
            SupplierSyncService service = new SupplierSyncService(f.client, callback, null, true, "001", f.mapper);
            fails(IntegrationException.Kind.WRITEBACK, () -> service.synchronize(input(), "11", "tenant-a"));
            equal(1, f.client.tokens);
            equal(1, f.client.creates);
            equal(1, f.callbacks.get());
        });
        check("transport failure propagates without another vendor request", () -> {
            Fixture f = fixture();
            f.client.vendorFailure = true;
            fails(IntegrationException.Kind.TRANSPORT, () -> f.service.synchronize(input(), "11", "tenant-a"));
            equal(1, f.client.creates);
            equal(0, f.callbacks.get());
        });
        check("business rejection blocks callback", () -> {
            Fixture f = fixture(); f.client.vendorReply = "{\"code\":0,\"success\":\"false\",\"message\":\"fixture-secret\"}";
            fails(IntegrationException.Kind.REJECTED, () -> f.service.synchronize(input(), "11", "tenant-a"));
            equal(0, f.callbacks.get()); equal(1, f.client.creates);
        });
        check("HTTP-success-shaped but wrong business code is rejected", () -> {
            Fixture f = fixture(); f.client.tokenReply = "{\"code\":200,\"success\":\"true\",\"message\":\"fixture-token\"}";
            fails(IntegrationException.Kind.REJECTED, () -> f.service.synchronize(input(), "11", "tenant-a")); equal(0, f.client.creates);
        });
        check("fractional success code is not accepted as integer one", () -> {
            Fixture f = fixture(); f.client.tokenReply = "{\"code\":1.2,\"success\":\"true\",\"message\":\"fixture-token\"}";
            fails(IntegrationException.Kind.REJECTED, () -> f.service.synchronize(input(), "11", "tenant-a")); equal(0, f.client.creates);
        });
        check("token missing message does not send vendor request", () -> {
            Fixture f = fixture(); f.client.tokenReply = "{\"code\":1,\"success\":\"true\",\"data\":[]}";
            fails(IntegrationException.Kind.VALIDATION, () -> f.service.synchronize(input(), "11", "tenant-a")); equal(0, f.client.creates);
        });
        check("invalid token JSON does not send vendor request", () -> {
            Fixture f = fixture(); f.client.tokenReply = "not-json";
            fails(IntegrationException.Kind.PROTOCOL, () -> f.service.synchronize(input(), "11", "tenant-a")); equal(0, f.client.creates);
        });
        check("missing vendor identifier blocks callback", () -> {
            Fixture f = fixture(); f.client.vendorReply = "{\"code\":1,\"success\":\"true\",\"data\":[]}";
            fails(IntegrationException.Kind.PROTOCOL, () -> f.service.synchronize(input(), "11", "tenant-a")); equal(0, f.callbacks.get());
        });
        check("required mapping and date validation happen before authentication", () -> {
            Fixture f = fixture(); SupplierSubmission bad = input(); bad.getMainData().remove("gysmc");
            fails(IntegrationException.Kind.VALIDATION, () -> f.service.synchronize(bad, "11", "tenant-a"));
            bad.getMainData().put("gysmc", "Fixture");
            f.developmentDate = "2026-02-30";
            fails(IntegrationException.Kind.VALIDATION, () -> f.service.synchronize(bad, "11", "tenant-a")); equal(0, f.client.tokens);
        });
        check("numeric bank account and unresolved E10 browser object are rejected", () -> {
            Fixture f = fixture(); SupplierSubmission bad = input(); bad.getMainData().put("yxzh", 1234L);
            fails(IntegrationException.Kind.VALIDATION, () -> f.service.synchronize(bad, "11", "tenant-a"));
            bad.getMainData().put("yxzh", "00001234"); bad.getMainData().put("ssfl", Collections.singletonMap("value", "01"));
            fails(IntegrationException.Kind.VALIDATION, () -> f.service.synchronize(bad, "11", "tenant-a")); equal(0, f.client.tokens);
        });
        check("disabled configuration never invokes transport", () -> {
            Fixture f = fixture();
            SupplierSyncService disabled = new SupplierSyncService(f.client, (s, r, u, t) -> {}, null, false, "001", f.mapper);
            fails(IntegrationException.Kind.CONFIGURATION, () -> disabled.synchronize(input(), "11", "tenant-a"));
            fails(IntegrationException.Kind.CONFIGURATION, () -> disabled.synchronize(101L, 11L, "tenant-a"));
            equal(0, f.client.tokens);
        });
        check("empty write-back constants prevent an E10 update", () -> {
            E10SupplierSuccessCallback callback = new E10SupplierSuccessCallback(new PublishKitRuntimeUtil() {

                /**
                 * 在回写字段留空的测试中拒绝创建 E10 RPC 服务。
                 *
                 * @param <T> RPC 服务类型
                 * @param type RPC 服务接口
                 * @param group RPC 服务组
                 * @param app E10 应用 ID
                 * @return RPC 服务，测试中通过抛出断言错误结束调用
                 */
                @Override
                public <T> T buildRpcService(Class<T> type, String group, String app) {
                    throw new AssertionError("Empty dataKeys must not reach E10");
                }
            });
            VendorCreated result = new VendorCreated();
            result.setVendorCode("0007");
            fails(IntegrationException.Kind.WRITEBACK, () -> callback.afterSuccess(input(), result, "11", "tenant-a"));
        });
        check("verified real dataKeys map explicitly to third-party DTO", () -> {
            Fixture f = fixture(); SupplierSubmission source = input();
            source.getMainData().put("ssfl", "0001"); source.getMainData().put("ssyx", "Verified bank"); source.getMainData().put("bz", "CNY");
            VendorArchive a = f.mapper.apply(source).getArchives();
            equal("0001", a.getCategoryCode()); equal("Verified bank", a.getBankName()); equal("CNY", a.getCurrencyName());
            equal("00001234", a.getBankAccount()); equal("0007", a.getVendorCode());
        });
        check("production mapping blocks missing development date before any transport", () -> {
            Fixture f = fixture(); SupplierSubmission source = input();
            source.getMainData().put("dVenDevDate", "2026-11-05"); source.getMainData().put("sqrq", "2026-11-05");
            SupplierSyncService production = new SupplierSyncService(f.client,
                    (s, r, u, t) -> f.callbacks.incrementAndGet(), null, true, "001", new SupplierMapper()::map);
            fails(IntegrationException.Kind.VALIDATION, () -> production.synchronize(source, "11", "tenant-a"));
            equal(0, f.client.tokens); equal(0, f.client.creates); equal(0, f.callbacks.get());
        });
        check("old example bank key cannot replace the verified yxzh field", () -> {
            SupplierSubmission source = input(); source.getMainData().remove("yxzh"); source.getMainData().put("yhzh", "00001234");
            fails(IntegrationException.Kind.VALIDATION, () -> new SupplierMapper().map(source, "2026-11-05"));
        });
        check("typed response accepts documented string and boolean success", () -> {
            Fixture f = fixture(); f.client.tokenReply = "{\"code\":1,\"success\":true,\"message\":\"fixture-token\",\"data\":[]}";
            f.service.synchronize(input(), "11", "tenant-a"); equal(1, f.client.creates);
        });
        check("numeric returned vendor code is not coerced into a valid identifier", () -> {
            Fixture f = fixture(); f.client.vendorReply = "{\"code\":1,\"success\":true,\"data\":[7]}";
            fails(IntegrationException.Kind.PROTOCOL, () -> f.service.synchronize(input(), "11", "tenant-a")); equal(0, f.callbacks.get());
        });
        check("requestId Action keeps main data and groups detail cells by table and row", () -> {
            Fixture f = fixture();
            SupplierSuccessCallback callback = (source, result, employee, tenant) -> {
                equal("103", source.getFormDataId());
                equal("902", source.getFormId());
                equal("104", source.getAppId());
                equal("11", employee);
                equal("tenant-a", tenant);
                Map<String, List<Map<String, Object>>> details = source.getDetailData();
                equal(3, details.size());
                List<Map<String, Object>> rows = details.get("supplier_materials");
                equal(2, rows.size());
                equal("物料一", rows.get(0).get("gyswl"));
                equal("评审通过", rows.get(0).get("glyppj"));
                equal("物料二", rows.get(1).get("gyswl"));
                Object optionValue = rows.get(1).get("sfzr");
                FormDataOption option = (FormDataOption) ((List<?>) optionValue).get(0);
                equal("1", option.getOptionId());
                equal("另一张表的物料", details.get("supplier_reviews").get(0).get("gyswl"));
                equal("0007", source.getMainData().get("gysbm"));
                require(!source.getMainData().containsKey("gyswl"));
                f.callbacks.incrementAndGet();
            };
            SupplierSyncService service = new SupplierSyncService(f.client, callback, reader(902L, "TEST-BILL-101"),
                    true, "00998", f.mapper);
            SupplierCreateAction action = new SupplierCreateAction(service);
            TenantRpcContext.setTargetTenantKey("tenant-a");
            TenantRpcContext.setTargetEmployeeId("11");
            try {
                WeaResult<Map<String, Object>> result = action.execute(Collections.<String, Object>singletonMap("requestId", 101));
                if (result.getData() == null) {
                    throw new AssertionError(result.getMsg());
                }
                equal("0007", result.getData().get("vendorCode"));
                JSONObject body = JSON.parseObject(f.client.vendorBody);
                equal("TEST-BILL-101", body.getString("billid"));
                equal("00998", body.getString("AccId"));
                equal("0007", body.getJSONObject("oArchives").getString("cVenCode"));
                equal("00001234", body.getJSONObject("oArchives").getString("cVenAccount"));
                equal(1, f.callbacks.get());
                require(action.execute(Collections.<String, Object>singletonMap("requestid", "9223372036854775808"))
                        .getMsg().contains("超出"));
                require(action.execute(Collections.emptyMap()).getMsg().contains("缺少 requestId"));
                Map<String, Object> conflictingIds = new HashMap<>();
                conflictingIds.put("requestId", "101");
                conflictingIds.put("requestid", 102L);
                require(action.execute(conflictingIds).getMsg().contains("不一致"));
                equal(1, f.client.tokens);
            } finally {
                TenantRpcContext.removeTargetEmployeeId();
                TenantRpcContext.removeFrameworkEmployeeId();
                TenantRpcContext.removeFrameworkTenantKey();
                MainThreadTagContainer.jobKeyThreadContext.remove();
                InheritThreadLocalContainer.remove();
            }
        });
        check("main-only workflow returns empty detail data without querying table metadata", () -> {
            SimpleEmployee employee = new SimpleEmployee(11L);
            employee.setTenantKey("tenant-a");
            WorkflowFormData source = workflowReader(903L, "TEST-BILL-101", null, null)
                    .readByRequestId(101L, employee);
            equal("0007", source.getMainData().get("gysbm"));
            require(source.getDetailData().isEmpty());
        });
        check("detail data uses table dataKeys across environments and handles empty, missing or duplicate keys", () -> {
            SimpleEmployee employee = new SimpleEmployee(11L);
            employee.setTenantKey("tenant-a");
            WorkflowFormData source = workflowReader(903L, "TEST-BILL-101", 901L, "supplier_reviews")
                    .readByRequestId(101L, employee, "104");
            equal(903L, source.getFormId());
            equal(103L, source.getFormDataId());
            equal("TEST-BILL-101", source.getRequestMark());
            equal("0007", source.getMainData().get("gysbm"));
            equal("物料一", source.getDetailData().get("supplier_materials").get(0).get("gyswl"));
            require(source.getDetailData().get("supplier_attachments").isEmpty());
            WorkflowFormReader missingKey = workflowReader(903L, "TEST-BILL-101", 901L, null);
            mockAppAndTableMetadata(missingKey, 903L, 104L, Collections.emptyList());
            fails(IntegrationException.Kind.VALIDATION, () -> missingKey.readByRequestId(101L, employee, "104"));
            fails(IntegrationException.Kind.VALIDATION,
                    () -> workflowReader(903L, "TEST-BILL-101", 901L, "supplier_materials")
                            .readByRequestId(101L, employee, "104"));
        });
        check("dynamic app ID routes the lookup for a missing detail table dataKey", () -> {
            SimpleEmployee employee = new SimpleEmployee(11L);
            employee.setTenantKey("tenant-a");
            WorkflowFormReader reader = workflowReader(903L, "TEST-BILL-101", 901L, null);
            Obj table = new Obj();
            table.setId(5902L);
            table.setFormId("902");
            table.setDataKey("supplier_reviews");
            mockAppAndTableMetadata(reader, 903L, 904L, Collections.singletonList(table));
            WorkflowFormData source = reader.readByRequestId(101L, employee);
            equal("904", source.getAppId());
            equal("另一张表的物料", source.getDetailData().get("supplier_reviews").get(0).get("gyswl"));
        });
        check("dynamic app ID lookup blocks when the workflow has no app mapping", () -> {
            SimpleEmployee employee = new SimpleEmployee(11L);
            employee.setTenantKey("tenant-a");
            WorkflowFormReader reader = workflowReader(903L, "TEST-BILL-101", 901L, "supplier_reviews");
            mockAppAndTableMetadata(reader, 903L, null, Collections.emptyList());
            fails(IntegrationException.Kind.VALIDATION, () -> reader.readByRequestId(101L, employee));
            equal("104", reader.readByRequestId(101L, employee, "104").getAppId());
        });
        check("missing form ID, fields, request mark and account block before authentication", () -> {
            Fixture f = fixture();
            SupplierSyncService missingFormId = new SupplierSyncService(f.client, (s, r, u, t) -> {},
                    reader(0L, "TEST-BILL-101"), true, "001", f.mapper);
            fails(IntegrationException.Kind.VALIDATION, () -> missingFormId.synchronize(101L, 11L, "tenant-a"));
            SupplierSyncService missingValue = new SupplierSyncService(f.client, (s, r, u, t) -> {},
                    reader(102L, "TEST-BILL-101", "gysmc"), true, "001", f.mapper);
            fails(IntegrationException.Kind.VALIDATION, () -> missingValue.synchronize(101L, 11L, "tenant-a"));
            SupplierSyncService missingMark = new SupplierSyncService(f.client, (s, r, u, t) -> {},
                    reader(102L, null), true, "001", f.mapper);
            fails(IntegrationException.Kind.VALIDATION, () -> missingMark.synchronize(101L, 11L, "tenant-a"));
            SupplierSyncService missingAccount = new SupplierSyncService(f.client, (s, r, u, t) -> {},
                    reader(102L, "TEST-BILL-101"), true, "", f.mapper);
            fails(IntegrationException.Kind.VALIDATION, () -> missingAccount.synchronize(101L, 11L, "tenant-a"));
            equal(0, f.client.tokens);
        });
        System.out.println("PASS: " + passed + " offline tests; network requests=0; real E10 writes=0");
    }

    /**
     * 为应用归属和表配置查询提供 eBuilder 元数据 RPC 替身。
     *
     * @param reader 待注入的读取组件
     * @param formId 期望查询的流程表单 ID
     * @param appId 模拟查到的应用 ID，可空
     * @param tables 主表对象关联的测试表配置，传 null 时禁止补查表配置
     */
    private static void mockAppAndTableMetadata(WorkflowFormReader reader, long formId, Long appId, List<Obj> tables) {
        Obj master = new Obj();
        master.setId(5001L);
        RemoteEBObjService remote = (RemoteEBObjService) Proxy.newProxyInstance(
                RemoteEBObjService.class.getClassLoader(), new Class<?>[]{RemoteEBObjService.class},
                (proxy, method, args) -> {
                    equal("tenant-a", args[1]);
                    if ("getObjListByFormId".equals(method.getName())) {
                        equal(Collections.singletonList(formId), args[0]);
                        return WeaResult.success(Collections.singletonList(master));
                    }
                    equal("getAllTablesByMasterId", method.getName());
                    equal(5001L, args[0]);
                    return tables;
                });
        EbuilderFlowForSpecializedApp flowRemote = (EbuilderFlowForSpecializedApp) Proxy.newProxyInstance(
                EbuilderFlowForSpecializedApp.class.getClassLoader(), new Class<?>[]{EbuilderFlowForSpecializedApp.class},
                (proxy, method, args) -> {
                    equal("getAppIdByWorkflowIds", method.getName());
                    equal(Collections.singletonList(201L), args[0]);
                    SimpleEmployee employee = (SimpleEmployee) args[1];
                    equal(11L, employee.getEmployeeId());
                    equal("tenant-a", employee.getTenantKey());
                    return appId == null ? Collections.emptyMap() : Collections.singletonMap(201L, appId);
                });
        inject(reader, "publishKit", new PublishKitRuntimeUtil() {

            /**
             * 提供与业务应用 ID 不同的默认路由标识。
             *
             * @return 测试用默认应用路由
             */
            @Override
            public String getDefaultAppId() {
                return "100";
            }

            /**
             * 返回当前应用路由下的 eBuilder 元数据 RPC 替身。
             *
             * @param <T> RPC 接口类型
             * @param type RPC 服务接口
             * @param group RPC 服务组
             * @param app E10 应用 ID
             * @return 测试用元数据服务
             */
            @Override
            public <T> T buildRpcService(Class<T> type, String group, String app) {
                if (type == EbuilderFlowForSpecializedApp.class) {
                    equal("ebuildercard", group);
                    equal("100", app);
                    return type.cast(flowRemote);
                }
                if (tables == null) {
                    throw new AssertionError("Unexpected detail table metadata lookup");
                }
                equal(RemoteEBObjService.class, type);
                equal("ebuilderform", group);
                equal(String.valueOf(appId), app);
                return type.cast(remote);
            }
        });
    }

    /**
     * 构造使用 E10 RPC 替身的表单读取组件。
     *
     * @param relatedFormId 流程实际关联的内部表单 ID
     * @param requestMark 流程实例的编号，可空
     * @param omittedDataKeys 需要省略值的字段
     * @return 使用合成表单定义和记录的读取组件
     */
    private static SupplierFormReader reader(long relatedFormId, String requestMark, String... omittedDataKeys) {
        return new SupplierFormReader(workflowReader(relatedFormId, requestMark, 301L, "supplier_reviews", omittedDataKeys));
    }

    /**
     * 构造可以模拟不同环境表 ID 的 E10 读取组件。
     *
     * @param relatedFormId 流程实际关联的内部表单 ID
     * @param requestMark 流程实例的编号，可空
     * @param firstDetailTableId 第一个测试明细表的 ID，传 null 时表单只有主表
     * @param secondTableKey 第二张明细表的数据键，可空
     * @param omittedDataKeys 需要省略值的字段
     * @return 使用合成定义和记录的读取组件
     */
    private static WorkflowFormReader workflowReader(long relatedFormId, String requestMark,
                                             Long firstDetailTableId, String secondTableKey, String... omittedDataKeys) {
        WorkflowFormReader reader = new WorkflowFormReader();
        WfpRelateFormEntity form = new WfpRelateFormEntity();
        form.setRelateType(WfpRelateFormType.WEA_EBUILDER);
        form.setRelateKey(relatedFormId);
        RequestFormInfoEntity relation = new RequestFormInfoEntity();
        relation.setRelateFormObj(form);
        relation.setDataId(103L);
        WfcRequestRpcEntity request = new WfcRequestRpcEntity();
        request.setRequestId(101L);
        request.setWorkflowId(201L);
        request.setRequestMark(requestMark);
        request.setFormInfos(Collections.singletonList(relation));
        WfcGetRequestDataRest requestRest = (WfcGetRequestDataRest) Proxy.newProxyInstance(
                WfcGetRequestDataRest.class.getClassLoader(), new Class<?>[]{WfcGetRequestDataRest.class},
                (proxy, method, args) -> {
                    equal("getRequestEntity", method.getName());
                    equal(101L, args[0]);
                    SimpleEmployee employee = (SimpleEmployee) args[1];
                    equal(11L, employee.getEmployeeId());
                    equal("tenant-a", employee.getTenantKey());
                    return request;
                });
        List<FormField> fields = new ArrayList<>();
        List<FormDataDetail> details = new ArrayList<>();
        Map<String, Object> values = input().getMainData();
        values.put("lcbh", "FORM-FIELD-VALUE");
        long fieldId = 1000L;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            FormField field = new FormField();
            field.setId(++fieldId);
            field.setDataKey(entry.getKey());
            field.setFormId(relatedFormId);
            fields.add(field);
            if (!Arrays.asList(omittedDataKeys).contains(entry.getKey())) {
                details.add(detail(fieldId, (String) entry.getValue()));
            }
        }
        if (firstDetailTableId != null) {
            SubForm firstTable = new SubForm();
            firstTable.setId(firstDetailTableId);
            firstTable.setDataKey("supplier_materials");
            SubForm firstTableReference = new SubForm();
            firstTableReference.setId(firstDetailTableId);
            SubForm secondTable = new SubForm();
            secondTable.setId(firstDetailTableId + 1);
            SubForm secondTableWithKey = new SubForm();
            secondTableWithKey.setId(firstDetailTableId + 1);
            secondTableWithKey.setDataKey(secondTableKey);
            SubForm emptyTable = new SubForm();
            emptyTable.setId(firstDetailTableId + 2);
            emptyTable.setDataKey("supplier_attachments");
            fields.add(detailField(2001L, "gyswl", firstTable));
            fields.add(detailField(2002L, "glyppj", firstTable));
            fields.add(detailField(2003L, "sfzr", firstTable));
            fields.add(detailField(2004L, "gyswl", secondTable));
            fields.add(detailField(2005L, "yppjfjsc", emptyTable));
            details.add(detailCell(2001L, "物料二", firstTableReference, 2L));
            details.add(detailCell(2001L, "物料一", firstTableReference, 1L));
            details.add(detailCell(2002L, "评审通过", null, 1L));
            FormDataOption option = new FormDataOption();
            option.setOptionId("1");
            FormDataDetail optionCell = detailCell(2003L, null, firstTableReference, 2L);
            optionCell.setDataOptions(Collections.singletonList(option));
            details.add(optionCell);
            details.add(detailCell(2004L, "另一张表的物料", secondTableWithKey, 1L));
        }
        FormData formData = new FormData();
        formData.setDataDetails(details);
        WfcFormFieldRest fieldRest = (WfcFormFieldRest) Proxy.newProxyInstance(
                WfcFormFieldRest.class.getClassLoader(), new Class<?>[]{WfcFormFieldRest.class},
                (proxy, method, args) -> {
                    if ("findFieldByWorkflowId".equals(method.getName())) {
                        equal(201L, args[0]);
                        return fields;
                    }
                    equal("getFormDataByRequestId", method.getName());
                    equal(101L, args[0]);
                    return formData;
                });
        inject(reader, "requestRest", requestRest);
        inject(reader, "fieldRest", fieldRest);
        mockAppAndTableMetadata(reader, relatedFormId, 104L, null);
        return reader;
    }

    /**
     * 生成测试用的明细字段定义。
     *
     * @param fieldId 字段 ID
     * @param key 字段 dataKey
     * @param table 字段所属明细表
     * @return 明细字段定义
     */
    private static FormField detailField(long fieldId, String key, SubForm table) {
        FormField field = new FormField();
        field.setId(fieldId);
        field.setDataKey(key);
        field.setSubForm(table);
        return field;
    }

    /**
     * 生成某张明细表中一行的测试单元格。
     *
     * @param fieldId 字段 ID
     * @param value 原始文本值
     * @param table 数据项的明细表引用，可以省略
     * @param index E10 返回的明细行下标
     * @return 带行下标的表单数据项
     */
    private static FormDataDetail detailCell(long fieldId, String value, SubForm table, long index) {
        FormDataDetail cell = detail(fieldId, value);
        cell.setSubForm(table);
        cell.setDataIndex(index);
        return cell;
    }

    /**
     * 生成仅带字段 ID 的 E10 数据项。
     *
     * @param fieldId 元数据字段 ID
     * @param value 原始文本值
     * @return 用于测试字段 ID 关联的表单数据项
     */
    private static FormDataDetail detail(long fieldId, String value) {
        FormField reference = new FormField();
        reference.setId(fieldId);
        FormDataDetail detail = new FormDataDetail();
        detail.setFormField(reference);
        detail.setContent(value);
        return detail;
    }

    /**
     * 在离线测试中为 RPC 注入点提供替身。
     *
     * @param reader 表单读取组件
     * @param name RPC 字段名
     * @param service RPC 替身
     */
    private static void inject(WorkflowFormReader reader, String name, Object service) {
        try {
            Field field = WorkflowFormReader.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(reader, service);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot inject offline RPC", e);
        }
    }

    /**
     * 为测试用例创建独立的夹具。
     *
     * @return 包含模拟客户端和同步服务的测试夹具
     */
    private static Fixture fixture() {
        return new Fixture();
    }

    /**
     * 构造包含必需表单字段的动作输入。
     *
     * @return 离线测试使用的动作输入
     */
    private static SupplierSubmission input() {
        SupplierSubmission s = new SupplierSubmission(); s.setFormId("102"); s.setFormDataId("103"); s.setAppId("104");
        s.setBillId("TEST-BILL-101"); s.setAccId("001"); s.setLoginDate(""); s.setMainData(new LinkedHashMap<>());
        for (String field : FIELDS) s.getMainData().put(field, "Fixture");
        s.getMainData().put("gysbm", "0007");
        s.getMainData().put("yxzh", "00001234");
        return s;
    }
    private static final class Fixture {
        final FakeVendorClient client;
        final AtomicInteger callbacks = new AtomicInteger();
        final SupplierSyncService service;
        // 合成日期由测试夹具提供，避免混入 E10 表单输入。
        String developmentDate = "2026-11-05";
        final java.util.function.Function<SupplierSubmission, AddVendorRequest> mapper = source ->
                new SupplierMapper().map(source, developmentDate);

        /**
         * 组装模拟客户端、字段映射和回写计数器。
         */
        Fixture() {
            client = new FakeVendorClient();
            service = new SupplierSyncService(client, (s, r, u, t) -> callbacks.incrementAndGet(), null, true, "001", mapper);
        }
    }
    private static final class FakeVendorClient extends VendorClient {

        /**
         * 使用模拟凭据和超时创建离线客户端。
         */
        FakeVendorClient() {
            super("fixture-app", "fixture-secret", 5000, 15000);
        }
        int tokens, creates; boolean vendorFailure; String tokenBody, vendorBody;
        String tokenReply = "{\"code\":1,\"success\":\"true\",\"message\":\"fixture-token\",\"data\":[]}";
        String vendorReply = "{\"code\":1,\"success\":\"true\",\"message\":\"ok\",\"data\":[\"0007\"]}";

        /**
         * 记录第三方请求，并按接口路径返回测试响应。
         *
         * @param path 相对于第三方根地址的接口路径
         * @param json JSON 请求内容
         * @return 为当前用例设置的模拟响应正文
         */
        @Override
        protected String post(String path, String json) {
            if (path.endsWith("Api/Base/GetToken")) {
                tokens++;
                tokenBody = json;
                return tokenReply;
            }
            if (!path.endsWith("Api/Vendor/Add")) throw new AssertionError("Unknown endpoint");
            creates++; vendorBody = json;
            if (vendorFailure) throw new IntegrationException(IntegrationException.Kind.TRANSPORT, "simulated timeout"); return vendorReply;
        }
    }

    /**
     * 执行一个用例，并记录通过结果。
     *
     * @param name 用例名称
     * @param test 待执行的测试逻辑
     */
    private static void check(String name, Runnable test) {
        test.run();
        passed++;
        System.out.println("PASS " + name);
    }

    /**
     * 断言实际值等于预期值。
     *
     * @param expected 预期值
     * @param actual 实际值
     */
    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    /**
     * 断言指定条件成立。
     *
     * @param condition 待检查的条件
     */
    private static void require(boolean condition) {
        if (!condition) {
            throw new AssertionError("Condition failed");
        }
    }

    /**
     * 断言指定操作抛出预期类别的集成异常。
     *
     * @param kind 预期异常类别
     * @param action 待执行的操作
     */
    private static void fails(IntegrationException.Kind kind, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected " + kind);
        } catch (IntegrationException e) {
            equal(kind, e.getKind());
            require(!e.getMessage().contains("fixture-secret") && !e.getMessage().contains("fixture-token"));
        }
    }
}
