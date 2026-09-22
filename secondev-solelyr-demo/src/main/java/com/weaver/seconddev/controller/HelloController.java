package com.weaver.seconddev.controller;

import com.weaver.common.authority.annotation.WeaPermission;
import com.weaver.common.base.entity.result.WeaResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
/**
 * @DESCRIPTION:
 * @USER: solelyr
 * @DATE: 2026/9/22 9:35
 */
@RestController
@RequestMapping("/api/secondev/solelyr/demo")
@WeaPermission(publicPermission = true)
public class HelloController {

    @GetMapping("/hello")
    public WeaResult<String> hello(@RequestParam("msg") String msg) {
        return WeaResult.success("Hello, " + msg);
    }

}