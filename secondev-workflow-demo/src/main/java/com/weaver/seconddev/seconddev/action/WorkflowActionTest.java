package com.weaver.seconddev.seconddev.action;

import com.weaver.common.base.entity.result.WeaResult;
import com.weaver.seconddev.action.WorkflowAction;
import com.weaver.seconddev.util.WorkflowUtil;
import com.weaver.verupgrade.soa.workflow.request.RequestInfo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * @DESCRIPTION:
 * @USER: solelyr
 * @DATE: 2026/6/22 16:33
 */
@Service("workflowActionTest")
public class WorkflowActionTest extends WorkflowAction {
    @Override
    public WeaResult<Map<String, Object>> doExecute(RequestInfo requestInfo) {
//        if(true) throw new RuntimeException("测试异常");
        String requestId = requestInfo.getRequestid();
        Map<String,Object> mainData = WorkflowUtil.getMainData(requestInfo);
        List<Map<String,Object>> dataList = WorkflowUtil.getDetailData(requestInfo,0);
        List<Map<String,Object>> dataList_1 = WorkflowUtil.getDetailData(requestInfo,1);

        return WeaResult.success();
    }
}
