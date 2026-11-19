package com.weaver.seconddev.action;

import com.alibaba.fastjson.JSON;

import com.weaver.seconddev.solelyr.workflow.action.WorkflowEsbActionHandler;
import com.weaver.seconddev.solelyr.workflow.model.WorkflowEsbForm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Map;

/**
 * @USER: solelyr
 * @DATE: 2026/11/14 20:05
 * @DESCRIPTION: 演示工作流 ESB 策略读取并返回表单主表数据
 */
@Slf4j
@Service("WorkflowEsbActionTest")
public class WorkflowEsbActionTest implements WorkflowEsbActionHandler<Map<String, Object>> {

    @Override
    public Map<String, Object> execute(WorkflowEsbForm workflowEsbForm) {
        log.error("测试日志输出：{}", JSON.toJSONString(workflowEsbForm));
        return workflowEsbForm.getMainData();
    }
}
