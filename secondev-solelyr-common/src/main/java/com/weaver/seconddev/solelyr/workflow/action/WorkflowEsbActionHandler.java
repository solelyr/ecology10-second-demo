package com.weaver.seconddev.solelyr.workflow.action;

import com.weaver.seconddev.solelyr.workflow.model.WorkflowEsbForm;

/**
 * @USER: solelyr
 * @DATE: 2026/11/14 21:53
 * @DESCRIPTION: 处理工作流表单并返回自定义策略结果
 */
@FunctionalInterface
public interface WorkflowEsbActionHandler<R> {
    /**
     * 处理已查询的表单数据。返回值应能转换为 JSON 对象，或为 null。
     * @throws com.weaver.seconddev.solelyr.workflow.exception.WorkflowEsbException 策略执行失败，异常消息会返回给调用方
     */
    R execute(WorkflowEsbForm result);
}
