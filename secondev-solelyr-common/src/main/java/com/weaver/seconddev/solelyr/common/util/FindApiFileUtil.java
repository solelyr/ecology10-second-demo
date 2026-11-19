package com.weaver.seconddev.solelyr.common.util;


import org.reflections.Reflections;
import org.springframework.web.bind.annotation.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @USER: solelyr
 * @DATE: 2026/11/19 9:37
 * @DESCRIPTION: 根据接口路径查找到对应的文件
 */
public class FindApiFileUtil {
    public static String  findApiFile(List<Class<?>> classes, String apiPath) {
        for (Class<?> c : classes) {
            RequestMapping path = c.getAnnotation(RequestMapping.class);
            // 该类的Path注解是否包含指定路径
            if (Arrays.stream(path.value()).anyMatch(i -> i.contains(apiPath))) {
                // 获取该类的所有方法
                Method[] methods = c.getMethods();
                for (Method m : methods) {
                    // 该方法是否使用 GetMapping 注解，并且该注解是否包含指定接口路径
                    if (m.isAnnotationPresent(GetMapping.class)) {
                        GetMapping methodPath = m.getAnnotation(GetMapping.class);
                        if ( Arrays.stream(methodPath.value()).anyMatch(i -> i.contains(apiPath))) {
                            return c.getName();
                        }
                    } else if (m.isAnnotationPresent(PostMapping.class)) {
                        PostMapping methodPath = m.getAnnotation(PostMapping.class);
                        if ( Arrays.stream(methodPath.value()).anyMatch(i -> i.contains(apiPath))) {
                            return c.getName();
                        }
                    } else if (m.isAnnotationPresent(PutMapping.class)) {
                        PutMapping methodPath = m.getAnnotation(PutMapping.class);
                        if ( Arrays.stream(methodPath.value()).anyMatch(i -> i.contains(apiPath))) {
                            return c.getName();
                        }
                    } else if (m.isAnnotationPresent(DeleteMapping.class)) {
                        DeleteMapping methodPath = m.getAnnotation(DeleteMapping.class);
                        if ( Arrays.stream(methodPath.value()).anyMatch(i -> i.contains(apiPath))) {
                            return c.getName();
                        }
                    } else if (m.isAnnotationPresent(RequestMapping.class)) {
                        RequestMapping methodPath = m.getAnnotation(RequestMapping.class);
                        if ( Arrays.stream(methodPath.value()).anyMatch(i -> i.contains(apiPath))) {
                            return c.getName();
                        }
                    }
                }
            }
        }
        return "";
    }

    public static List<Class<?>> findApiClasses(String packagePrefix, String apiPath) {
        List<Class<?>> result = new ArrayList<>();
        // 可以在构造方法加上包路径，这样就会扫描指定包下的类，如 com.api ,如不指定包名则会扫描所有类，这样会比较慢
        Reflections f = new Reflections(packagePrefix);
        // 找到所有使用Path注解的类
        Set<Class<?>> set = f.getTypesAnnotatedWith(RequestMapping.class);
        for (Class<?> c : set) {
            RequestMapping path = c.getAnnotation(RequestMapping.class);
            // 该类的Path注解是否包含指定路径
            if (path != null && Arrays.stream(path.value()).anyMatch(i -> i.contains(apiPath))) {
                result.add(c);
            }
        }
        return result;
    }
}
