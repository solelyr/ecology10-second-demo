package com.weaver.seconddev.solelyr.journal.config;

import com.weaver.seconddev.solelyr.journal.entity.JournalUnit;
import com.weaver.seconddev.solelyr.journal.service.JournalDto;
import com.weaver.seconddev.solelyr.journal.executor.JournalExecutor;
import com.weaver.seconddev.solelyr.journal.exception.JournalErrors;
import com.weaver.seconddev.solelyr.journal.service.JournalService;
import com.weaver.common.distribution.lock.util.DistributionLockUtil;
import com.weaver.ebuilder.form.client.service.data.RemoteSimpleDataService;
import com.weaver.ebuilder.form.client.service.emobile.IEtFormDatasetService;
import com.weaver.framework.rpc.annotation.RpcReference;
import com.weaver.publishkit.api.util.PublishKitRuntimeUtil;
import com.weaver.seconddev.solelyr.workflow.exception.WorkflowEsbException;
import com.weaver.seconddev.solelyr.workflow.util.SqlExecutor;
import com.weaver.seconddev.solelyr.workflow.util.SecondevDataSourceGroup;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @USER: solelyr
 * @DATE: 2026/11/23 19:28
 * @DESCRIPTION: 注册公共推送执行器并按租户提供 E10 日志和幂等状态表访问
 */
@Configuration
public class WorkflowPushCoreConfiguration {
    /** 用于读取日志表和幂等状态表的元数据及记录。 */
    @RpcReference(group = "ebuilderform")
    private IEtFormDatasetService datasetService;
    /** 用于按应用创建表单写入 RPC 服务。 */
    private final PublishKitRuntimeUtil publishKit;
    /** 按当前租户和物理表名查询 eBuilder 表单所属应用。 */
    private final SqlExecutor sqlExecutor;

    public WorkflowPushCoreConfiguration(SqlExecutor sqlExecutor, PublishKitRuntimeUtil publishKit) {
        this.sqlExecutor = sqlExecutor;
        this.publishKit = publishKit;
    }

    @Bean
    public JournalExecutor workflowPushExecutor() {
        return new JournalExecutor(this::openJournal, DistributionLockUtil::getBean);
    }

    private JournalService openJournal(JournalUnit unit) {
        return JournalService.forForms(unit, () -> openForm(unit.tenant, "uf_dsfjkdyrzb"),
                () -> openForm(unit.tenant, "uf_dsfjkmdztb"));
    }

    private JournalDto openForm(String tenantKey, String tableName) {
        String appId = resolveAppId(tenantKey, tableName);
        RemoteSimpleDataService formDataService = publishKit.buildRpcService(RemoteSimpleDataService.class, "ebuilderform", appId);
        return new JournalDto(datasetService, formDataService, appId, tenantKey, tableName);
    }

    String resolveAppId(String tenantKey, String tableName) {
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
