package com.sealmail.web.controller.v1;

import com.sealmail.app.dto.response.SystemSettingsResponse;
import com.sealmail.app.security.UserContext;
import com.sealmail.app.usecase.config.QuerySystemSettingsUseCase;
import com.sealmail.web.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system-settings")
@RequiredArgsConstructor
@Tag(name = "系统设置", description = "只读运行配置与运维状态")
public class SystemSettingsController {

    private final QuerySystemSettingsUseCase querySystemSettingsUseCase;

    @GetMapping
    @Operation(summary = "查询系统设置快照", description = "返回脱敏后的运行配置。启动参数不支持通过 API 在线修改。")
    public ApiResponse<SystemSettingsResponse> getSettings(@AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(querySystemSettingsUseCase.getSettings(user));
    }
}
