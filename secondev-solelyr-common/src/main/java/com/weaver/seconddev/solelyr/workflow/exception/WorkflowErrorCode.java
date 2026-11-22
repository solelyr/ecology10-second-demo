package com.weaver.seconddev.solelyr.workflow.exception;

/**
 * @USER: solelyr
 * @DATE: 2026/11/22 08:01
 * @DESCRIPTION: 定义业务模块可自行实现的稳定工作流错误码
 */
@FunctionalInterface
public interface WorkflowErrorCode {
    String code();
}
