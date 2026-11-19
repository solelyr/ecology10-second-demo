package com.weaver.seconddev.chatgpt.seconddev.demo.supplier.service;

import com.weaver.seconddev.chatgpt.seconddev.integration.workflow.WorkflowFormReader;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.action.SupplierSubmission;
import com.weaver.seconddev.chatgpt.seconddev.integration.workflow.WorkflowFormData;
import com.weaver.teams.domain.user.SimpleEmployee;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @USER: solelyr
 * @DATE: 2026/11/09 22:00
 * @DESCRIPTION: 使用公共流程表单读取组件组装供应商映射输入
 */
@Component
public final class SupplierFormReader {
    private final com.weaver.seconddev.chatgpt.seconddev.integration.workflow.WorkflowFormReader workflowFormReader;

    /**
     * 创建供应商表单适配组件。
     *
     * @param workflowFormReader 公共流程表单读取组件
     */
    @Autowired
    public SupplierFormReader(WorkflowFormReader workflowFormReader) {
        this.workflowFormReader = workflowFormReader;
    }

    /**
     * 将流程表单结果组装成供应商同步输入。
     *
     * @param requestId 当前流程实例 ID
     * @param employee 当前用户和租户上下文
     * @return 供应商映射和回写所需的输入
     */
    public SupplierSubmission read(long requestId, SimpleEmployee employee) {
        WorkflowFormData form = workflowFormReader.readByRequestId(requestId, employee);
        SupplierSubmission input = new SupplierSubmission();
        input.setAppId(form.getAppId());
        input.setFormId(Long.toString(form.getFormId()));
        input.setFormDataId(Long.toString(form.getFormDataId()));
        input.setBillId(form.getRequestMark());
        input.setMainData(form.getMainData());
        input.setDetailData(form.getDetailData());
        return input;
    }
}
