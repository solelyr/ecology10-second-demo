package com.weaver.seconddev.demo;

import com.weaver.common.base.entity.result.WeaResult;
import com.weaver.common.authority.annotation.WeaPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/secondev/demo")
@WeaPermission(publicPermission = true)
public class HelloController {

    @GetMapping("/hello")
    public WeaResult<String> hello(@RequestParam("msg") String msg) {
        return WeaResult.success("Hello, " + msg);
    }
}
