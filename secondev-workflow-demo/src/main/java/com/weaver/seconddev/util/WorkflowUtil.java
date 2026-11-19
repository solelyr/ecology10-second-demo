package com.weaver.seconddev.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.weaver.verupgrade.soa.workflow.request.*;

/**
 * @USER: solelyr
 * @DATE: 2026/09/22 11:02
 * @DESCRIPTION: 从流程请求中提取主表和明细表数据
 */
public class WorkflowUtil {
    private WorkflowUtil() {
    }

    /**
     * 获取主表数据
     * @param requestInfo 流程基本信息
     * @return
     */
    public static Map<String, Object> getMainData(RequestInfo requestInfo) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", requestInfo.getRequestManager() == null
                ? toString(requestInfo.getRequestid())
                : toString(requestInfo.getRequestManager().getBillid()));
        map.put("requestid", toString(requestInfo.getRequestid()));
        for (Property property : requestInfo.getMainTableInfo().getProperty()) map.put( property.getName().toLowerCase(), property.getValue());
        return map;
    }

    /**
     * 获取明细表数据 从0开始代表明细表1
     * @param requestInfo 流程基本信息
     * @param detailNum 明细表序号 从0开始
     * @return
     */
    public static List<Map<String, Object>> getDetailData(RequestInfo requestInfo, int detailNum) {
        Map<String, Object> map;
        List<Map<String, Object>> list = new ArrayList<>();
        DetailTable[] detailtable = requestInfo.getDetailTableInfo().getDetailTable();
        if (detailtable.length > 0 && detailtable.length > detailNum) {
            DetailTable dt = detailtable[detailNum];
            Row[] s = dt.getRow();
            for (int j = 0; j < s.length; j++) {
                map = new HashMap<>();
                Row r = s[j];
                map.put("id",r.getId());
                for (Cell cell : r.getCell()) map.put(cell.getName().toLowerCase(),cell.getValue());
                list.add(map);
            }
        }
        return list;
    }

    private static String toString(Object value) {
        return value == null ? "" : value.toString();
    }
}
