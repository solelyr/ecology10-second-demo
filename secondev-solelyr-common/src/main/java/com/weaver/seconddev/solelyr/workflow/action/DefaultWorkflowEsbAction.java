package com.weaver.seconddev.solelyr.workflow.action;

import com.weaver.seconddev.solelyr.workflow.service.WorkflowFormQueryService;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * @USER: solelyr
 * @DATE: 2026/11/14 19:39
 * @DESCRIPTION: 以示例 Bean 名称暴露统一工作流 ESB 入口
 */
@Service("solelyrWorkflowEsbActionDemo")
public class DefaultWorkflowEsbAction extends WorkflowEsbAction {
    public DefaultWorkflowEsbAction(WorkflowFormQueryService service,
                                          Map<String, WorkflowEsbActionHandler<?>> handlers) {
        super(service, handlers);
    }
}
