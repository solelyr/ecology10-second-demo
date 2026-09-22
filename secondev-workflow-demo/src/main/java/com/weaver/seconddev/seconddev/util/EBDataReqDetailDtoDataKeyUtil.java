package com.weaver.seconddev.seconddev.util;

import com.weaver.ebuilder.form.client.entity.data.EBDataReqDetailDto;

/**
 * @DESCRIPTION:
 * @USER: solelyr
 * @DATE: 2026/6/24 15:05
 */
public class EBDataReqDetailDtoDataKeyUtil {

    public static EBDataReqDetailDto setData(String dataKey, String content) {
        EBDataReqDetailDto dataReqDetailDto = new EBDataReqDetailDto();
        dataReqDetailDto.setDataKey(dataKey);
        dataReqDetailDto.setContent(content);
        return dataReqDetailDto;
    }
}
