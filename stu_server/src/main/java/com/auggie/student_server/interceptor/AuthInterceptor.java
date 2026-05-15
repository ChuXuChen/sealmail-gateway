package com.auggie.student_server.interceptor;

import com.auggie.student_server.annotation.RequiresRoles;
import com.auggie.student_server.entity.Teacher;
import com.auggie.student_server.service.TeacherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;

/**
 * 权限校验拦截器
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private TeacherService teacherService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 如果不是控制器方法的请求，直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        // 获取方法上的RequiresRoles注解
        RequiresRoles requiresRoles = handlerMethod.getMethodAnnotation(RequiresRoles.class);
        // 如果方法上没有，尝试获取类上的注解
        if (requiresRoles == null) {
            requiresRoles = handlerMethod.getBeanType().getAnnotation(RequiresRoles.class);
        }

        // 如果没有配置权限注解，直接放行
        if (requiresRoles == null) {
            return true;
        }

        // 获取请求头中的用户信息
        String userType = request.getHeader("X-User-Type");
        String userIdStr = request.getHeader("X-User-Id");

        // 如果用户信息为空，返回401未授权
        if (userType == null || userType.isEmpty() || userIdStr == null || userIdStr.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("请先登录");
            return false;
        }

        Integer userId;
        try {
            userId = Integer.parseInt(userIdStr);
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("用户ID格式错误");
            return false;
        }

        // 转换用户类型为统一的数字格式
        String role = convertToRoleCode(userType);
        // 如果是管理员角色（type=3或者admin），需要验证用户确实是管理员
        if ("3".equals(role)) {
            Teacher teacher = teacherService.findById(userId);
            if (teacher == null || !"admin".equals(teacher.getTname())) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("没有管理员权限");
                return false;
            }
        }

        // 校验用户角色是否在允许的列表中
        List<String> allowedRoles = Arrays.asList(requiresRoles.value());
        if (allowedRoles.contains(role)) {
            return true;
        } else {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("没有权限访问该资源");
            return false;
        }
    }

    /**
     * 转换用户类型为统一的数字角色代码
     * 学生: 1, 教师: 2, 管理员: 3
     */
    private String convertToRoleCode(String userType) {
        if ("1".equals(userType) || "student".equalsIgnoreCase(userType)) {
            return "1";
        } else if ("2".equals(userType) || "teacher".equalsIgnoreCase(userType)) {
            return "2";
        } else if ("3".equals(userType) || "admin".equalsIgnoreCase(userType)) {
            return "3";
        }
        return userType;
    }
}
