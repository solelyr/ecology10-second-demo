package com.weaver.seconddev.seconddev.demo;

import com.weaver.common.authority.annotation.WeaPermission;
import com.weaver.common.base.entity.result.WeaResult;
import com.weaver.seconddev.action.EBDataChangeTest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @DESCRIPTION:
 * @USER: solelyr
 * @DATE: 2026/5/27 上午10:47
 */
@RestController
@RequestMapping("/api/secondev/demo")
@WeaPermission(publicPermission = true)
public class HelloController {

    @GetMapping("/hello")
    public WeaResult<String> hello(@RequestParam("msg") String msg) {
        return WeaResult.success("Hello, " + msg);
    }

    @Resource
    private EBDataChangeTest ebDataChangeTest;

    @GetMapping("/test")
    public WeaResult<String> test(@RequestParam("msg") String msg) {
        ebDataChangeTest.doSave();
        return WeaResult.success("测试成功！");
    }

    @GetMapping("/test1")
    public WeaResult<String> test1(@RequestParam("msg") String msg) {
        return WeaResult.success("测试结果返回成功！");
    }
}