package com.weaver.seconddev.solelyr.journal.config;

import com.weaver.seconddev.solelyr.ebuilder.util.EbUtil;
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
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;

/**
 * @USER: solelyr
 * @DATE: 2026/11/23 19:28
 * @DESCRIPTION: 注册公共推送执行器并按租户提供 E10 日志和幂等状态表访问
 */
@Configuration
@DependsOn("ebUtil")
@Import(EbUtil.class)
public class WorkflowPushCoreConfiguration {
    /** 用于读取日志表和幂等状态表的元数据及记录。 */
    @RpcReference(group = "ebuilderform")
    private IEtFormDatasetService datasetService;
    /** 用于按应用创建表单写入 RPC 服务。 */
    private final PublishKitRuntimeUtil publishKit;


    public WorkflowPushCoreConfiguration(PublishKitRuntimeUtil publishKit) {
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
        String appId = EbUtil.resolveAppId(tenantKey, tableName);
        RemoteSimpleDataService formDataService = publishKit.buildRpcService(RemoteSimpleDataService.class, "ebuilderform", appId);
        return new JournalDto(datasetService, formDataService, appId, tenantKey, tableName);
    }


}
