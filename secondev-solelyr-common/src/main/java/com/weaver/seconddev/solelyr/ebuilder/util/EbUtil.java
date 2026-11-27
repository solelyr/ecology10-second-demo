package com.weaver.seconddev.solelyr.ebuilder.util;

import com.weaver.seconddev.solelyr.journal.exception.JournalErrors;
import com.weaver.seconddev.solelyr.workflow.exception.WorkflowEsbException;
import com.weaver.seconddev.solelyr.workflow.util.SecondevDataSourceGroup;
import com.weaver.seconddev.solelyr.workflow.util.SqlExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * @USER: solelyr
 * @DATE: 2026/11/27 23:23
 * @DESCRIPTION: Eb模块的自定义工具类
 */
@Component("ebUtil")
public class EbUtil {

    /** 按当前租户和物理表名查询 eBuilder 表单所属应用。 */
    private static volatile SqlExecutor sqlExecutor;

    /**
     * 由 Spring 构造注入查询器，初始化静态应用解析入口。
     * @param sqlExecutor 平台 SQL 查询组件
     */
    @Autowired
    public EbUtil(SqlExecutor sqlExecutor) {
        setSqlExecutor(sqlExecutor);
    }

    /**
     * 设置静态查询器，供初始化及离线测试恢复依赖使用。
     * @param sqlExecutor SQL 查询组件
     */
    public static void setSqlExecutor(SqlExecutor sqlExecutor) {
        EbUtil.sqlExecutor = sqlExecutor;
    }

    /**
     * 通过表单名称查询EB表对应的ID
     * @param tenantKey
     * @param tableName
     * @return
     */
    public static String resolveAppId(String tenantKey, String tableName) {
        List<Map<String, Object>> rows;
        try {
            rows = sqlExecutor.executeQuery(SecondevDataSourceGroup.EBUILDER_APP_GROUP_ID,
                    "select app_id from ebdf_obj where table_name = ? and tenant_key = ? and delete_type = ?",
                    tableName, tenantKey, 0);
        } catch (RuntimeException failure) {
            throw new WorkflowEsbException(JournalErrors.STORAGE, "查询 OA 表单所属应用失败：" + tableName, failure);
        }
        if (rows == null || rows.size() != 1 || rows.get(0) == null) {
            throw new WorkflowEsbException(JournalErrors.STORAGE, "当前租户的 OA 表单不存在或不唯一：" + tableName);
        }
        Map<String, Object> row = rows.get(0);
        Object value = row.getOrDefault("app_id", row.get("APP_ID"));
        String appId = value == null ? "" : value.toString().trim();
        if (appId.isEmpty()) {
            throw new WorkflowEsbException(JournalErrors.STORAGE, "OA 表单缺少所属应用 ID：" + tableName);
        }
        return appId;
    }
}
