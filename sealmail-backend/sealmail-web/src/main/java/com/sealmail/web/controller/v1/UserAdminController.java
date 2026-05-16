package com.sealmail.web.controller.v1;

import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.dto.response.UserSummaryResponse;
import com.sealmail.app.security.UserContext;
import com.sealmail.app.usecase.auth.UserAccountUseCase;
import com.sealmail.web.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "超级管理员用户管理")
public class UserAdminController {

    private final UserAccountUseCase userAccountUseCase;

    @GetMapping
    @Operation(summary = "查询用户列表")
    public ApiResponse<List<UserSummaryResponse>> list(@AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(userAccountUseCase.list(user));
    }

    @PostMapping("/{userId}/disable")
    @Operation(summary = "禁用用户")
    public ApiResponse<UserSummaryResponse> disable(@PathVariable String userId,
                                                    @AuthenticationPrincipal UserContext user,
                                                    HttpServletRequest request) {
        return ApiResponse.ok(userAccountUseCase.disable(userId, user, clientIp(request)));
    }

    @PostMapping("/{userId}/enable")
    @Operation(summary = "启用用户")
    public ApiResponse<UserSummaryResponse> enable(@PathVariable String userId,
                                                   @AuthenticationPrincipal UserContext user,
                                                   HttpServletRequest request) {
        return ApiResponse.ok(userAccountUseCase.enable(userId, user, clientIp(request)));
    }

    @PostMapping("/{userId}/unlock")
    @Operation(summary = "解锁用户")
    public ApiResponse<UserSummaryResponse> unlock(@PathVariable String userId,
                                                   @AuthenticationPrincipal UserContext user,
                                                   HttpServletRequest request) {
        return ApiResponse.ok(userAccountUseCase.unlock(userId, user, clientIp(request)));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

}
