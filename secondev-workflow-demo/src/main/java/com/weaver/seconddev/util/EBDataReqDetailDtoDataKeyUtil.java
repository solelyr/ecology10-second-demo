package com.weaver.seconddev.util;

import com.weaver.ebuilder.form.client.entity.data.EBDataReqDetailDto;

/**
 * @USER: solelyr
 * @DATE: 2026/6/24 15:05
 * @DESCRIPTION: 根据字段 dataKey 和内容构造 eBuilder 数据请求明细
 */
public class EBDataReqDetailDtoDataKeyUtil {

    public static EBDataReqDetailDto setData(String dataKey, String content) {
        EBDataReqDetailDto dataReqDetailDto = new EBDataReqDetailDto();
        dataReqDetailDto.setDataKey(dataKey);
        dataReqDetailDto.setContent(content);
        return dataReqDetailDto;
    }
}
