package com.weaver.seconddev.solelyr.workflow.util;

import com.weaver.common.form.metadata.field.FormField;
import com.weaver.seconddev.solelyr.workflow.TestSupport;
import com.weaver.seconddev.solelyr.workflow.exception.WorkflowErrors;

/**
 * @USER: solelyr
 * @DATE: 2026/11/11 15:10
 * @DESCRIPTION: 验证数据库列名解析顺序、无列字段和非法列名
 */
public final class ColumnNameResolverTest {
    /**
     * 测试入口类，禁止实例化。
     */
    private ColumnNameResolverTest() {
    }

    /**
     * 运行全部用例。
     *
     * @param args 未使用
     */
    public static void main(String[] args) {
        TestSupport.check("columnName 属性优先", () -> {
            FormField f = TestSupport.mainField(1, "gysbm", "gysbm_col");
            f.setOtherProperties("{\"columnName\":\"other_col\"}");
            TestSupport.eq("gysbm_col", ColumnNameResolver.resolve(f));
        });
        TestSupport.check("columnName 为空时读取 otherProperties", () -> {
            FormField f = TestSupport.mainField(2, "gysmc", "  ");
            f.setOtherProperties("{\"dataKey\":\"gysmc\",\"columnName\":\" gysmc_0 \"}");
            TestSupport.eq("gysmc_0", ColumnNameResolver.resolve(f));
        });
        TestSupport.check("无列名返回 null，不回退为 dataKey", () -> {
            FormField f = TestSupport.mainField(3, "layout", null);
            f.setOtherProperties("not-json");
            TestSupport.eq(null, ColumnNameResolver.resolve(f));
        });
        TestSupport.check("物理列 form_table_field.column_name 最优先", () -> {
            FormField f = TestSupport.mainField(5, "k", "from_field");
            TestSupport.eq("from_physical", ColumnNameResolver.resolve(f, TestSupport.column(5, " from_physical ", "t")));
            TestSupport.eq("from_field", ColumnNameResolver.resolve(f, TestSupport.column(5, null, "t")));
        });
        TestSupport.check("null 字段返回 null", () -> TestSupport.eq(null, ColumnNameResolver.resolve(null)));
        TestSupport.check("非法列名被拒绝", () -> TestSupport.expect(WorkflowErrors.FIELD_METADATA_INVALID,
                () -> ColumnNameResolver.resolve(TestSupport.mainField(4, "x", "a;drop table"))));
        TestSupport.check("isSafeIdentifier 边界", () -> {
            TestSupport.eq(true, ColumnNameResolver.isSafeIdentifier("uf_gys_mx1"));
            TestSupport.eq(false, ColumnNameResolver.isSafeIdentifier("1abc"));
            TestSupport.eq(false, ColumnNameResolver.isSafeIdentifier(null));
        });
        TestSupport.finish("ColumnNameResolverTest");
    }
}
