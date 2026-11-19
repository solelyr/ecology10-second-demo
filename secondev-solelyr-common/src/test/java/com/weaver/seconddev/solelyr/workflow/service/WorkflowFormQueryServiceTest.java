package com.weaver.seconddev.solelyr.workflow.service;

import com.weaver.common.form.data.FormData;
import com.weaver.common.form.data.detail.FileDetail;
import com.weaver.common.form.data.detail.FormDataDetail;
import com.weaver.common.form.data.option.FormDataOption;
import com.weaver.common.form.data.text.FormDataText;
import com.weaver.common.form.metadata.field.FormField;
import com.weaver.common.form.metadata.subform.SubForm;
import com.weaver.common.form.physical.entity.FormTable;
import com.weaver.common.form.physical.entity.FormTableField;
import com.weaver.seconddev.solelyr.workflow.TestSupport;
import com.weaver.seconddev.solelyr.workflow.exception.WorkflowEsbException;
import com.weaver.seconddev.solelyr.workflow.model.WorkflowFieldInfo;
import com.weaver.seconddev.solelyr.workflow.model.WorkflowEsbForm;
import com.weaver.teams.domain.user.SimpleEmployee;
import com.weaver.workflow.common.entity.rpc.WfcRequestRpcEntity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @USER: solelyr
 * @DATE: 2026/11/11 15:10
 * @DESCRIPTION: 离线验证按 requestId 查询、数据库列名转换、明细分组及失败分支
 */
public final class WorkflowFormQueryServiceTest {
    private static final SubForm DETAIL = TestSupport.subForm(500L, "mx1", "uf_test_dt1");
    private static final FormField CODE = TestSupport.mainField(1, "gysbm", "gysbm");
    private static final FormField NAME = TestSupport.mainField(2, "gysmc", "gys_name");
    private static final FormField LAYOUT = TestSupport.mainField(3, "desc", null);
    private static final FormField ITEM = TestSupport.detailField(11, "wlmc", "wlmc", DETAIL);
    private static final FormField QTY = TestSupport.detailField(12, "sl", "sl", DETAIL);

    /**
     * 测试入口类，禁止实例化。
     */
    private WorkflowFormQueryServiceTest() {
    }

    /**
     * 运行全部用例。
     *
     * @param args 未使用
     */
    public static void main(String[] args) {
        TestSupport.check("正常路径：主表、明细按数据库列名输出并保留流程上下文", () -> {
            FormDataDetail longText = TestSupport.cell(NAME, null, null, null);
            FormDataText text = new FormDataText();
            text.setContent("长文本名称");
            longText.setDataText(text);
            com.weaver.seconddev.solelyr.workflow.model.WorkflowEsbForm r = service(fields(), TestSupport.formData("uf_test",
                    TestSupport.cell(CODE, "007", null, null), longText,
                    TestSupport.cell(ITEM, "B", 1L, 801L), TestSupport.cell(QTY, "2", 1L, 801L),
                    TestSupport.cell(ITEM, "A", 0L, 800L), TestSupport.cell(QTY, "1", 0L, 800L))).query(101L, TestSupport.employee());
            TestSupport.eq(101L, r.getRequestId());
            TestSupport.eq(77L, r.getWorkflowId());
            TestSupport.eq("LC-001", r.getRequestMark());
            TestSupport.eq(300L, r.getFormId());
            TestSupport.eq(9001L, r.getDataId());
            TestSupport.eq("uf_test", r.getMainTableName());
            TestSupport.eq("9001", r.getMainValue("id"));
            TestSupport.eq("007", r.getMainValue("gysbm"));
            TestSupport.eq("长文本名称", r.getMainValue("gys_name"));
            TestSupport.eq(null, r.getMainData().get("gysmc"));
            List<Map<String, Object>> rows = r.getDetailRows("uf_test_dt1");
            TestSupport.eq(2, rows.size());
            TestSupport.eq("800", rows.get(0).get("id"));
            TestSupport.eq("A", rows.get(0).get("wlmc"));
            TestSupport.eq("1", rows.get(0).get("sl"));
            TestSupport.eq("B", rows.get(1).get("wlmc"));
        });
        TestSupport.check("字段信息包含列名、表名、明细标识，无列字段 columnName 为空", () -> {
            com.weaver.seconddev.solelyr.workflow.model.WorkflowEsbForm r = service(fields(), TestSupport.formData("uf_test")).query(101L, TestSupport.employee());
            TestSupport.eq(5, r.getFields().size());
            com.weaver.seconddev.solelyr.workflow.model.WorkflowFieldInfo name = r.getFields().get(1);
            TestSupport.eq("gysmc", name.getDataKey());
            TestSupport.eq("gys_name", name.getColumnName());
            TestSupport.eq("uf_test", name.getTableName());
            TestSupport.eq(false, name.isDetail());
            TestSupport.eq(null, r.getFields().get(2).getColumnName());
            WorkflowFieldInfo item = r.getFields().get(3);
            TestSupport.eq(true, item.isDetail());
            TestSupport.eq("uf_test_dt1", item.getTableName());
            TestSupport.eq("mx1", item.getSubFormDataKey());
            TestSupport.eq(500L, item.getSubFormId());
        });
        TestSupport.check("已定义但无数据的明细表映射为空列表", () -> {
            com.weaver.seconddev.solelyr.workflow.model.WorkflowEsbForm r = service(fields(), TestSupport.formData("uf_test", TestSupport.cell(CODE, "1", null, null)))
                    .query(101L, TestSupport.employee());
            TestSupport.eq(true, r.getDetailData().containsKey("uf_test_dt1"));
            TestSupport.eq(0, r.getDetailRows("uf_test_dt1").size());
            TestSupport.eq(0, r.getDetailRows("not_exists").size());
        });
        TestSupport.check("不在流程字段定义中的单元格被忽略", () -> {
            FormField unknown = TestSupport.mainField(99, "x", "x");
            com.weaver.seconddev.solelyr.workflow.model.WorkflowEsbForm r = service(fields(), TestSupport.formData("uf_test", TestSupport.cell(unknown, "v", null, null)))
                    .query(101L, TestSupport.employee());
            TestSupport.eq(false, r.getMainData().containsKey("x"));
        });
        TestSupport.check("toMap 输出长整数为字符串", () -> {
            WorkflowEsbForm result = service(fields(), TestSupport.formData("uf_test")).query(101L, TestSupport.employee());
            TestSupport.eq("t1", result.getTenantKey());
            TestSupport.eq(9L, result.getEmployeeId());
            Map<String, Object> m = result.toMap();
            TestSupport.eq("101", m.get("requestId"));
            TestSupport.eq("t1", m.get("tenantKey"));
            TestSupport.eq("9", m.get("employeeId"));
            TestSupport.eq("9001", m.get("dataId"));
            TestSupport.eq(true, m.get("mainData") instanceof Map);
        });
        TestSupport.check("requestId 非法", () -> TestSupport.expect(WorkflowEsbException.Kind.VALIDATION,
                () -> service(fields(), TestSupport.formData("uf_test")).query(0L, TestSupport.employee())));
        TestSupport.check("用户上下文缺失", () -> {
            SimpleEmployee noTenant = new SimpleEmployee(1L);
            TestSupport.expect(WorkflowEsbException.Kind.VALIDATION, () -> service(fields(), TestSupport.formData("t")).query(1L, noTenant));
            TestSupport.expect(WorkflowEsbException.Kind.VALIDATION, () -> service(fields(), TestSupport.formData("t")).query(1L, (SimpleEmployee) null));
            TestSupport.expect(WorkflowEsbException.Kind.VALIDATION, () -> service(fields(), TestSupport.formData("t")).query(1L, null, "t1"));
            TestSupport.expect(WorkflowEsbException.Kind.VALIDATION, () -> service(fields(), TestSupport.formData("t")).query(1L, 1L, " "));
        });
        TestSupport.check("平台依赖未注入时给出明确错误", () -> {
            TestSupport.expect(WorkflowEsbException.Kind.VALIDATION, () -> new WorkflowFormQueryService().query(1L, TestSupport.employee()));
            TestSupport.expect(WorkflowEsbException.Kind.VALIDATION, () -> new WorkflowFormQueryService(TestSupport.requestRest(null),
                    TestSupport.fieldRest(fields(), TestSupport.formData("t")), null, TestSupport.columnService(null)).query(1L, TestSupport.employee()));
        });
        TestSupport.check("流程实例不存在或 ID 不一致", () -> {
            TestSupport.expect(WorkflowEsbException.Kind.NOT_FOUND, () -> new WorkflowFormQueryService(TestSupport.requestRest(null),
                    TestSupport.fieldRest(fields(), TestSupport.formData("t")), TestSupport.tableService(null), TestSupport.columnService(null)).query(101L, TestSupport.employee()));
            TestSupport.expect(WorkflowEsbException.Kind.NOT_FOUND, () -> new WorkflowFormQueryService(TestSupport.requestRest(TestSupport.request(102L, 77L, 300L, 9001L)),
                    TestSupport.fieldRest(fields(), TestSupport.formData("t")), TestSupport.tableService(null), TestSupport.columnService(null)).query(101L, TestSupport.employee()));
        });
        TestSupport.check("流程未关联或关联多张表单", () -> {
            WfcRequestRpcEntity none = TestSupport.request(101L, 77L, 300L, 9001L);
            none.setFormInfos(Collections.emptyList());
            TestSupport.expect(WorkflowEsbException.Kind.NOT_FOUND, () -> new WorkflowFormQueryService(TestSupport.requestRest(none),
                    TestSupport.fieldRest(fields(), TestSupport.formData("t")), TestSupport.tableService(null), TestSupport.columnService(null)).query(101L, TestSupport.employee()));
            WfcRequestRpcEntity two = TestSupport.request(101L, 77L, 300L, 9001L);
            two.setFormInfos(Arrays.asList(two.getFormInfos().get(0), two.getFormInfos().get(0)));
            TestSupport.expect(WorkflowEsbException.Kind.NOT_FOUND, () -> new WorkflowFormQueryService(TestSupport.requestRest(two),
                    TestSupport.fieldRest(fields(), TestSupport.formData("t")), TestSupport.tableService(null), TestSupport.columnService(null)).query(101L, TestSupport.employee()));
        });
        TestSupport.check("字段定义为空或表单数据为空", () -> {
            TestSupport.expect(WorkflowEsbException.Kind.METADATA, () -> service(new ArrayList<FormField>(), TestSupport.formData("t")).query(101L, TestSupport.employee()));
            TestSupport.expect(WorkflowEsbException.Kind.NOT_FOUND, () -> service(fields(), null).query(101L, TestSupport.employee()));
        });
        TestSupport.check("有数据但缺少数据库列名时阻断，不回退 dataKey", () -> TestSupport.expect(WorkflowEsbException.Kind.METADATA,
                () -> service(fields(), TestSupport.formData("uf_test", TestSupport.cell(LAYOUT, "x", null, null))).query(101L, TestSupport.employee())));
        TestSupport.check("同一行重复列阻断", () -> {
            FormField dup = TestSupport.mainField(4, "other", "gysbm");
            List<FormField> list = new ArrayList<>(fields());
            list.add(dup);
            TestSupport.expect(WorkflowEsbException.Kind.METADATA, () -> service(list, TestSupport.formData("uf_test",
                    TestSupport.cell(CODE, "1", null, null), TestSupport.cell(dup, "2", null, null))).query(101L, TestSupport.employee()));
        });
        TestSupport.check("明细缺表名或行下标阻断", () -> {
            FormField noTable = TestSupport.detailField(21, "a", "a", TestSupport.subForm(600L, "mx2", null));
            TestSupport.expect(WorkflowEsbException.Kind.METADATA, () -> service(Collections.singletonList(noTable),
                    TestSupport.formData("t", TestSupport.cell(noTable, "1", 0L, null))).query(101L, TestSupport.employee()));
            TestSupport.expect(WorkflowEsbException.Kind.METADATA, () -> service(fields(),
                    TestSupport.formData("t", TestSupport.cell(ITEM, "1", null, null))).query(101L, TestSupport.employee()));
        });
        TestSupport.check("同一明细行的行 ID 不一致时阻断", () -> TestSupport.expect(WorkflowEsbException.Kind.METADATA, () -> service(fields(),
                TestSupport.formData("t", TestSupport.cell(ITEM, "1", 0L, 1L), TestSupport.cell(QTY, "2", 0L, 2L))).query(101L, TestSupport.employee())));
        TestSupport.check("运行时 SubForm 只有 id：按 form_table 解析主表和明细表名", () -> {
            SubForm idOnly = new SubForm();
            idOnly.setId(1334380634596532224L);
            FormField a = TestSupport.detailField(31, "a", null, idOnly);
            FormField b = TestSupport.detailField(32, "b", null, idOnly);
            FormField m = TestSupport.mainField(30, "m", null);
            List<FormTable> tables = Arrays.asList(TestSupport.table(300L, "uf_gysxz", 0),
                    TestSupport.table(1334380634596532224L, "uf_gysxz_dt1", 0));
            List<FormTableField> columns = Arrays.asList(TestSupport.column(30, "zbzd", "uf_gysxz"),
                    TestSupport.column(31, "mx_a", "uf_gysxz_dt1"), TestSupport.column(32, "mx_b", "uf_gysxz_dt1"));
            com.weaver.seconddev.solelyr.workflow.model.WorkflowEsbForm r = service(Arrays.asList(m, a, b), TestSupport.formData(null,
                    TestSupport.cell(m, "主", null, null), TestSupport.cell(a, "a1", 0L, 700L), TestSupport.cell(b, "b1", 0L, 700L)), tables, columns)
                    .query(101L, TestSupport.employee());
            TestSupport.eq("uf_gysxz", r.getMainTableName());
            TestSupport.eq("主", r.getMainValue("zbzd"));
            List<Map<String, Object>> rows = r.getDetailRows("uf_gysxz_dt1");
            TestSupport.eq(1, rows.size());
            TestSupport.eq("700", rows.get(0).get("id"));
            TestSupport.eq("a1", rows.get(0).get("mx_a"));
            TestSupport.eq("b1", rows.get(0).get("mx_b"));
            TestSupport.eq("uf_gysxz_dt1", r.getFields().get(1).getTableName());
            TestSupport.eq("mx_a", r.getFields().get(1).getColumnName());
            TestSupport.eq(1334380634596532224L, r.getFields().get(1).getSubFormId());
        });
        TestSupport.check("物理列名优先于 FormField.columnName", () -> {
            com.weaver.seconddev.solelyr.workflow.model.WorkflowEsbForm r = service(fields(), TestSupport.formData("uf_test", TestSupport.cell(CODE, "1", null, null)),
                    new ArrayList<FormTable>(), Collections.singletonList(TestSupport.column(1, "real_code", "uf_test")))
                    .query(101L, TestSupport.employee());
            TestSupport.eq("1", r.getMainValue("real_code"));
            TestSupport.eq(false, r.getMainData().containsKey("gysbm"));
        });
        TestSupport.check("form_table 缺明细时回退到物理列中的表名", () -> {
            SubForm idOnly = new SubForm();
            idOnly.setId(888L);
            FormField a = TestSupport.detailField(41, "a", null, idOnly);
            com.weaver.seconddev.solelyr.workflow.model.WorkflowEsbForm r = service(Collections.singletonList(a), TestSupport.formData("t", TestSupport.cell(a, "x", 0L, null)),
                    new ArrayList<FormTable>(), Collections.singletonList(TestSupport.column(41, "col_a", "uf_dt9")))
                    .query(101L, TestSupport.employee());
            TestSupport.eq("x", r.getDetailRows("uf_dt9").get(0).get("col_a"));
        });
        TestSupport.check("已定义但无数据的明细表也按 form_table 表名返回空列表", () -> {
            SubForm idOnly = new SubForm();
            idOnly.setId(889L);
            FormField a = TestSupport.detailField(42, "a", "a", idOnly);
            com.weaver.seconddev.solelyr.workflow.model.WorkflowEsbForm r = service(Collections.singletonList(a), TestSupport.formData("t"),
                    Collections.singletonList(TestSupport.table(889L, "uf_empty_dt", 0)), new ArrayList<FormTableField>())
                    .query(101L, TestSupport.employee());
            TestSupport.eq(true, r.getDetailData().containsKey("uf_empty_dt"));
            TestSupport.eq(0, r.getDetailRows("uf_empty_dt").size());
        });
        TestSupport.check("同一表单多张有效物理表阻断，已删除记录让位于有效记录", () -> {
            TestSupport.expect(WorkflowEsbException.Kind.METADATA, () -> service(fields(), TestSupport.formData("t"),
                    Arrays.asList(TestSupport.table(300L, "uf_a", 0), TestSupport.table(300L, "uf_b", 0)), new ArrayList<FormTableField>())
                    .query(101L, TestSupport.employee()));
            com.weaver.seconddev.solelyr.workflow.model.WorkflowEsbForm r = service(fields(), TestSupport.formData("t"),
                    Arrays.asList(TestSupport.table(300L, "uf_old", 1), TestSupport.table(300L, "uf_new", 0)), new ArrayList<FormTableField>())
                    .query(101L, TestSupport.employee());
            TestSupport.eq("uf_new", r.getMainTableName());
        });
        TestSupport.check("本地服务返回 null 时回退到流程 RPC 元数据", () -> {
            com.weaver.seconddev.solelyr.workflow.model.WorkflowEsbForm r = new WorkflowFormQueryService(TestSupport.requestRest(TestSupport.request(101L, 77L, 300L, 9001L)),
                    TestSupport.fieldRest(fields(), TestSupport.formData("uf_test", TestSupport.cell(ITEM, "A", 0L, null))), TestSupport.tableService(null),
                    TestSupport.columnService(null)).query(101L, TestSupport.employee());
            TestSupport.eq("uf_test", r.getMainTableName());
            TestSupport.eq("A", r.getDetailRows("uf_test_dt1").get(0).get("wlmc"));
        });
        TestSupport.check("下拉单选：值为 optionId，显示为选项名称", () -> {
            FormField select = TestSupport.mainField(51, "zt", "zt");
            FormDataDetail c = TestSupport.cell(select, null, null, null);
            c.setDataOptions(Collections.singletonList(option("1", "启用")));
            com.weaver.seconddev.solelyr.workflow.model.WorkflowEsbForm r = service(Collections.singletonList(select), TestSupport.formData("uf_t", c)).query(101L, TestSupport.employee());
            TestSupport.eq("1", r.getMainValue("zt"));
            TestSupport.eq("启用", r.getMainText("zt"));
        });
        TestSupport.check("复选/人员多值：optionId 与名称分别以英文逗号拼接", () -> {
            FormField people = TestSupport.mainField(52, "ry", "ry");
            FormDataDetail c = TestSupport.cell(people, null, null, null);
            c.setDataOptions(Arrays.asList(option("918327792617603074", "张三"), null,
                    option("5114723702119969714", "李四"), option(" ", null)));
            com.weaver.seconddev.solelyr.workflow.model.WorkflowEsbForm r = service(Collections.singletonList(people), TestSupport.formData("uf_t", c)).query(101L, TestSupport.employee());
            TestSupport.eq("918327792617603074,5114723702119969714", r.getMainValue("ry"));
            TestSupport.eq("张三,李四", r.getMainText("ry"));
        });
        TestSupport.check("附件（dataOptions type=file）：值为文件 ID，显示为文件名", () -> {
            FormField file = TestSupport.mainField(53, "fj", "fj");
            FormDataDetail c = TestSupport.cell(file, null, null, null);
            c.setDataOptions(Arrays.asList(option("1200694417425448961", "合同附件.pdf"),
                    option("1200694438975700994", "截图.png")));
            com.weaver.seconddev.solelyr.workflow.model.WorkflowEsbForm r = service(Collections.singletonList(file), TestSupport.formData("uf_t", c)).query(101L, TestSupport.employee());
            TestSupport.eq("1200694417425448961,1200694438975700994", r.getMainValue("fj"));
            TestSupport.eq("合同附件.pdf,截图.png", r.getMainText("fj"));
        });
        TestSupport.check("附件无 dataOptions 时取 fileDetails", () -> {
            FormField file = TestSupport.mainField(54, "fj2", "fj2");
            FormDataDetail c = TestSupport.cell(file, null, null, null);
            FileDetail f = new FileDetail();
            f.setFileId(777L);
            f.setFileName("a.docx");
            c.setFileDetails(Collections.singletonList(f));
            com.weaver.seconddev.solelyr.workflow.model.WorkflowEsbForm r = service(Collections.singletonList(file), TestSupport.formData("uf_t", c)).query(101L, TestSupport.employee());
            TestSupport.eq("777", r.getMainValue("fj2"));
            TestSupport.eq("a.docx", r.getMainText("fj2"));
        });
        TestSupport.check("明细中的下拉和附件写入 detailData 与 detailText", () -> {
            FormDataDetail sel = TestSupport.cell(ITEM, null, 0L, 800L);
            sel.setDataOptions(Collections.singletonList(option("6594238874381770918", "法属圭亚那")));
            FormDataDetail qty = TestSupport.cell(QTY, "3", 0L, 800L);
            com.weaver.seconddev.solelyr.workflow.model.WorkflowEsbForm r = service(fields(), TestSupport.formData("uf_test", sel, qty)).query(101L, TestSupport.employee());
            TestSupport.eq("6594238874381770918", r.getDetailRows("uf_test_dt1").get(0).get("wlmc"));
            TestSupport.eq("法属圭亚那", r.getDetailTextRows("uf_test_dt1").get(0).get("wlmc"));
            TestSupport.eq("800", r.getDetailTextRows("uf_test_dt1").get(0).get("id"));
            TestSupport.eq("3", r.getDetailTextRows("uf_test_dt1").get(0).get("sl"));
            TestSupport.eq(0, r.getDetailTextRows("none").size());
        });
        TestSupport.check("空选项列表回退到 content；toMap 含显示内容", () -> {
            FormDataDetail c = TestSupport.cell(CODE, "007", null, null);
            c.setDataOptions(new ArrayList<FormDataOption>());
            WorkflowEsbForm r = service(fields(), TestSupport.formData("uf_test", c)).query(101L, TestSupport.employee());
            TestSupport.eq("007", r.getMainValue("gysbm"));
            TestSupport.eq("007", r.getMainText("gysbm"));
            TestSupport.eq(true, r.toMap().get("mainText") instanceof Map);
            TestSupport.eq(true, r.toMap().get("detailText") instanceof Map);
        });
        TestSupport.check("非法表名被拒绝", () -> TestSupport.expect(WorkflowEsbException.Kind.METADATA,
                () -> service(fields(), TestSupport.formData("bad-name")).query(101L, TestSupport.employee())));
        TestSupport.finish("WorkflowFormQueryServiceTest");
    }

    /**
     * 构造选项型字段的数据选项。
     *
     * @param id 选项 ID、文件 ID 或人员 ID
     * @param content 显示内容
     * @return 数据选项
     */
    private static FormDataOption option(String id, String content) {
        FormDataOption option = new FormDataOption();
        option.setOptionId(id);
        option.setContent(content);
        return option;
    }

    /**
     * 返回默认的主表、无列布局字段和明细字段定义。
     *
     * @return 字段定义列表
     */
    private static List<FormField> fields() {
        return Arrays.asList(CODE, NAME, LAYOUT, ITEM, QTY);
    }

    /**
     * 创建不返回物理元数据的查询服务，用于验证回退到流程 RPC 元数据的路径。
     *
     * @param fields 字段定义
     * @param data 表单数据
     * @return 查询服务
     */
    private static WorkflowFormQueryService service(List<FormField> fields, FormData data) {
        return service(fields, data, new ArrayList<FormTable>(), new ArrayList<FormTableField>());
    }

    /**
     * 创建使用指定物理元数据的查询服务。
     *
     * @param fields 字段定义
     * @param data 表单数据
     * @param tables 物理表记录
     * @param columns 物理列记录
     * @return 查询服务
     */
    private static WorkflowFormQueryService service(List<FormField> fields, FormData data, List<FormTable> tables,
                                                    List<FormTableField> columns) {
        return new WorkflowFormQueryService(TestSupport.requestRest(TestSupport.request(101L, 77L, 300L, 9001L)), TestSupport.fieldRest(fields, data),
                TestSupport.tableService(tables), TestSupport.columnService(columns));
    }
}
