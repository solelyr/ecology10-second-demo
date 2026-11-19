package com.weaver.seconddev.solelyr.workflow.mapper;

import com.weaver.seconddev.solelyr.workflow.model.WorkflowEsbForm;

/**
 * @USER: solelyr
 * @DATE: 2026/11/14 22:40
 * @DESCRIPTION: 将工作流表单转换为业务实体
 *
 * @param <T> 目标实体类型
 */
@FunctionalInterface
public interface WorkflowEsbFormMapper<T> {
    T map(WorkflowEsbForm form);
}
