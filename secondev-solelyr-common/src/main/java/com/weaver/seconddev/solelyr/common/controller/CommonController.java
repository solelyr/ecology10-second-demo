package com.weaver.seconddev.solelyr.common.controller;

import com.weaver.common.authority.annotation.WeaPermission;
import com.weaver.common.base.entity.result.WeaResult;
import com.weaver.seconddev.solelyr.common.util.FindApiFileUtil;
import groovy.util.logging.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @USER: solelyr
 * @DATE: 2026/11/19 9:34
 * @DESCRIPTION: 通用的一些接口方法
 */
@Slf4j
@RestController
@RequestMapping("/api/secondev/common")
@WeaPermission(publicPermission = true)
public class CommonController {


    @GetMapping("/findApi")
    public WeaResult<List<Class<?>>> findApi(@RequestParam(value = "prefix", required = false) String prefix, @RequestParam(value = "apiPath", required = false) String apiPath) {
        return WeaResult.success(FindApiFileUtil.findApiClasses(prefix,apiPath));
    }
}