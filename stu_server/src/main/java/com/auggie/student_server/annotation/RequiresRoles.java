package com.auggie.student_server.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解
 * 用于标识接口需要的角色权限
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRoles {
    /**
     * 允许的角色列表
     * 1: 学生, 2: 教师, 3: 管理员
     */
    String[] value() default {};
}
