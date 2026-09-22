package com.weaver.seconddev.action;

import com.weaver.common.base.entity.result.WeaResult;
import com.weaver.seconddev.util.WorkflowUtil;
import com.weaver.verupgrade.soa.workflow.request.RequestInfo;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service("workflowActionTest")
public class WorkflowActionTest extends WorkflowAction {
    @Override
    public WeaResult<Map<String, Object>> doExecute(RequestInfo requestInfo) {
        Map<String, Object> result = new HashMap<>();
        result.put("requestId", requestInfo.getRequestid());
        result.put("mainData", WorkflowUtil.getMainData(requestInfo));

        List<Map<String, Object>> firstDetail = WorkflowUtil.getDetailData(requestInfo, 0);
        if (!firstDetail.isEmpty()) {
            result.put("firstDetail", firstDetail);
        }

        return WeaResult.success(result);
    }
}
