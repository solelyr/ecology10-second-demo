package com.weaver.seconddev.solelyr.workflow;

import com.weaver.common.form.data.FormData;
import com.weaver.common.form.data.detail.FormDataDetail;
import com.weaver.common.form.metadata.Form;
import com.weaver.common.form.metadata.field.FormField;
import com.weaver.common.form.metadata.subform.SubForm;
import com.weaver.common.form.physical.entity.FormTable;
import com.weaver.common.form.physical.entity.FormTableField;
import com.weaver.common.form.physical.service.FormTableFieldService;
import com.weaver.common.form.physical.service.FormTableService;
import com.weaver.seconddev.solelyr.workflow.exception.WorkflowErrorCode;
import com.weaver.seconddev.solelyr.workflow.exception.WorkflowEsbException;
import com.weaver.teams.domain.user.SimpleEmployee;
import com.weaver.workflow.common.entity.formdef.RequestFormInfoEntity;
import com.weaver.workflow.common.entity.formdef.WfpRelateFormEntity;
import com.weaver.workflow.common.entity.rpc.WfcRequestRpcEntity;
import com.weaver.workflow.core.api.rest.publicapi.WfcFormFieldRest;
import com.weaver.workflow.core.api.rest.publicapi.WfcGetRequestDataRest;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @USER: solelyr
 * @DATE: 2026/11/11 15:10
 * @DESCRIPTION: 离线测试公共工具：断言、合成流程数据及 RPC 替身
 */
public final class TestSupport {
    /** 当前进程内通过的用例数。 */
    static int passed;
    /** 当前进程内失败的用例数。 */
    static int failed;

    /**
     * 工具类，禁止实例化。
     */
    private TestSupport() {
    }

    /** 可抛异常的测试体。 */
    public interface Body {
        /**
         * 执行测试。
         *
         * @throws Exception 测试失败
         */
        void run() throws Exception;
    }

    /**
     * 执行单个用例并记录结果。
     *
     * @param name 用例名
     * @param body 用例体
     */
    public static void check(String name, Body body) {
        try {
            body.run();
            passed++;
            System.out.println("PASS " + name);
        } catch (Throwable e) {
            failed++;
            System.out.println("FAIL " + name + " -> " + e);
        }
    }

    /**
     * 断言相等。
     *
     * @param expected 期望值
     * @param actual 实际值
     */
    public static void eq(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    /**
     * 断言抛出指定错误码的查询异常。
     *
     * @param code 期望错误码
     * @param body 测试体
     * @throws Exception 测试体抛出的非查询异常
     */
    public static void expect(WorkflowErrorCode code, Body body) throws Exception {
        try {
            body.run();
        } catch (WorkflowEsbException e) {
            eq(code.code(), e.getErrorCode());
            return;
        }
        throw new AssertionError("expected WorkflowEsbException " + code.code());
    }

    /**
     * 输出汇总并在失败时返回非零退出码。
     *
     * @param suite 测试类名
     */
    public static void finish(String suite) {
        System.out.println(suite + ": passed=" + passed + ", failed=" + failed + ", skipped=0");
        if (failed > 0) {
            System.exit(1);
        }
    }

    /**
     * 构造用户上下文。
     *
     * @return 合成用户
     */
    public static SimpleEmployee employee() {
        SimpleEmployee employee = new SimpleEmployee(9L);
        employee.setTenantKey("t1");
        return employee;
    }

    /**
     * 构造主表字段定义。
     *
     * @param id 字段 ID
     * @param dataKey 字段 dataKey
     * @param column 数据库列名，可为 null
     * @return 主表字段
     */
    public static FormField mainField(long id, String dataKey, String column) {
        FormField field = new FormField();
        field.setId(id);
        field.setTitle("字段" + id);
        field.setDataKey(dataKey);
        field.setColumnName(column);
        return field;
    }

    /**
     * 构造明细字段定义。
     *
     * @param id 字段 ID
     * @param dataKey 字段 dataKey
     * @param column 数据库列名，可为 null
     * @param subForm 所属明细表
     * @return 明细字段
     */
    public static FormField detailField(long id, String dataKey, String column, SubForm subForm) {
        FormField field = mainField(id, dataKey, column);
        field.setSubForm(subForm);
        return field;
    }

    /**
     * 构造明细表定义。
     *
     * @param id 明细表 ID
     * @param dataKey 明细表 dataKey
     * @param tableName 明细表数据库表名，可为 null
     * @return 明细表定义
     */
    public static SubForm subForm(long id, String dataKey, String tableName) {
        SubForm subForm = new SubForm();
        subForm.setId(id);
        subForm.setDataKey(dataKey);
        subForm.setTableName(tableName);
        return subForm;
    }

    /**
     * 构造单元格数据；单元格只引用字段 ID，模拟平台返回的精简字段对象。
     *
     * @param field 所属字段
     * @param content 单元格内容
     * @param rowIndex 明细行下标，主表为 null
     * @param rowId 明细行记录 ID，可为 null
     * @return 单元格
     */
    public static FormDataDetail cell(FormField field, String content, Long rowIndex, Long rowId) {
        FormDataDetail detail = new FormDataDetail();
        FormField ref = new FormField();
        ref.setId(field.getId());
        detail.setFormField(ref);
        detail.setContent(content);
        detail.setDataIndex(rowIndex);
        detail.setRowId(rowId);
        return detail;
    }

    /**
     * 构造表单数据。
     *
     * @param mainTable 主表数据库表名，可为 null
     * @param cells 全部单元格
     * @return 表单数据
     */
    public static FormData formData(String mainTable, FormDataDetail... cells) {
        FormData data = new FormData();
        Form form = new Form();
        form.setTableName(mainTable);
        data.setForm(form);
        List<FormDataDetail> list = new ArrayList<>();
        Collections.addAll(list, cells);
        data.setDataDetails(list);
        return data;
    }

    /**
     * 构造只关联一张表单的流程实例。
     *
     * @param requestId 流程实例 ID
     * @param workflowId 工作流 ID
     * @param formId 关联表单 ID
     * @param dataId 表单数据 ID
     * @return 流程实例
     */
    public static WfcRequestRpcEntity request(long requestId, long workflowId, long formId, long dataId) {
        WfcRequestRpcEntity request = new WfcRequestRpcEntity();
        request.setRequestId(requestId);
        request.setWorkflowId(workflowId);
        request.setRequestName("测试流程");
        request.setRequestMark("LC-001");
        RequestFormInfoEntity info = new RequestFormInfoEntity();
        info.setDataId(dataId);
        WfpRelateFormEntity relate = new WfpRelateFormEntity();
        relate.setRelateKey(formId);
        info.setRelateFormObj(relate);
        request.setFormInfos(Collections.singletonList(info));
        return request;
    }

    /**
     * 创建流程实例 RPC 替身，getRequestEntity 返回指定实体。
     *
     * @param entity 要返回的流程实例，可为 null
     * @return 流程实例 RPC 替身
     */
    public static WfcGetRequestDataRest requestRest(final WfcRequestRpcEntity entity) {
        return (WfcGetRequestDataRest) Proxy.newProxyInstance(TestSupport.class.getClassLoader(),
                new Class<?>[]{WfcGetRequestDataRest.class}, (proxy, method, args) -> {
                    if ("getRequestEntity".equals(method.getName())) {
                        return entity;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
     * 创建表单字段与数据 RPC 替身。
     *
     * @param fields findFieldByWorkflowId 返回的字段定义
     * @param data getFormDataByRequestId 返回的表单数据
     * @return 表单字段与数据 RPC 替身
     */
    public static WfcFormFieldRest fieldRest(final List<FormField> fields, final FormData data) {
        return (WfcFormFieldRest) Proxy.newProxyInstance(TestSupport.class.getClassLoader(),
                new Class<?>[]{WfcFormFieldRest.class}, (proxy, method, args) -> {
                    if ("findFieldByWorkflowId".equals(method.getName())) {
                        return fields;
                    }
                    if ("getFormDataByRequestId".equals(method.getName()) && args.length == 2) {
                        return data;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
     * 构造物理表记录。
     *
     * @param formOrSubFormId form_table.form_id，即主表单 ID 或明细表 ID
     * @param tableName 物理表名
     * @param deleteState 删除状态，0 为有效
     * @return 物理表记录
     */
    public static FormTable table(long formOrSubFormId, String tableName, Integer deleteState) {
        FormTable table = new FormTable();
        Form form = new Form();
        form.setId(formOrSubFormId);
        table.setForm(form);
        table.setTableName(tableName);
        table.setDeleteState(deleteState);
        return table;
    }

    /**
     * 构造物理列记录。
     *
     * @param fieldId 字段 ID
     * @param columnName 数据库列名
     * @param tableName 所在物理表名
     * @return 物理列记录
     */
    public static FormTableField column(long fieldId, String columnName, String tableName) {
        FormTableField column = new FormTableField();
        FormField field = new FormField();
        field.setId(fieldId);
        column.setFormField(field);
        column.setColumnName(columnName);
        column.setTempContent(tableName);
        return column;
    }

    /**
     * 创建物理表服务替身，listFormTableByMainFormId 返回指定列表。
     *
     * @param tables 要返回的物理表，可为 null
     * @return 物理表服务替身
     */
    public static FormTableService tableService(final List<FormTable> tables) {
        return (FormTableService) Proxy.newProxyInstance(TestSupport.class.getClassLoader(),
                new Class<?>[]{FormTableService.class}, (proxy, method, args) -> {
                    if ("listFormTableByMainFormId".equals(method.getName())) {
                        return tables;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
     * 创建物理列服务替身，getByFieldIds 返回指定列表。
     *
     * @param columns 要返回的物理列，可为 null
     * @return 物理列服务替身
     */
    public static FormTableFieldService columnService(final List<FormTableField> columns) {
        return (FormTableFieldService) Proxy.newProxyInstance(TestSupport.class.getClassLoader(),
                new Class<?>[]{FormTableFieldService.class}, (proxy, method, args) -> {
                    if ("getByFieldIds".equals(method.getName()) && args.length == 2 && args[0] instanceof List) {
                        return columns;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
