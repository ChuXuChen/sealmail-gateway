package com.sealmail.web.controller.v1;

import com.sealmail.app.security.UserContext;
import com.sealmail.infra.dlp.config.DlpConfigService;
import com.sealmail.infra.dlp.config.DlpPatternConfig;
import com.sealmail.infra.dlp.config.DlpPatternUpdate;
import com.sealmail.infra.dlp.config.DlpSelectionConfig;
import com.sealmail.infra.dlp.config.DlpSelectionUpdate;
import com.sealmail.web.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dlp")
@Tag(name = "DLP", description = "DLP 规则、范围和隔离配置")
public class DlpController {

    private final DlpConfigService configService;

    public DlpController(DlpConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/patterns")
    @Operation(summary = "查询 DLP 规则")
    public ApiResponse<List<DlpPatternConfig>> listPatterns(@AuthenticationPrincipal UserContext user) {
        requireAdmin(user);
        return ApiResponse.ok(configService.listPatterns());
    }

    @PostMapping("/patterns")
    @Operation(summary = "创建 DLP 规则")
    public ApiResponse<DlpPatternConfig> createPattern(
            @RequestBody DlpPatternUpdate request,
            @AuthenticationPrincipal UserContext user) {
        requireAdmin(user);
        return ApiResponse.ok(configService.createPattern(request));
    }

    @PutMapping("/patterns/{id}")
    @Operation(summary = "更新 DLP 规则")
    public ApiResponse<DlpPatternConfig> updatePattern(
            @PathVariable String id,
            @RequestBody DlpPatternUpdate request,
            @AuthenticationPrincipal UserContext user) {
        requireAdmin(user);
        return ApiResponse.ok(configService.updatePattern(id, request));
    }

    @DeleteMapping("/patterns/{id}")
    @Operation(summary = "删除 DLP 规则")
    public ApiResponse<Void> deletePattern(
            @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        requireAdmin(user);
        configService.deletePattern(id);
        return ApiResponse.ok();
    }

    @GetMapping("/selections")
    @Operation(summary = "查询 DLP 生效范围")
    public ApiResponse<List<DlpSelectionConfig>> listSelections(@AuthenticationPrincipal UserContext user) {
        requireAdmin(user);
        return ApiResponse.ok(configService.listSelections());
    }

    @PostMapping("/selections")
    @Operation(summary = "创建 DLP 生效范围")
    public ApiResponse<DlpSelectionConfig> createSelection(
            @RequestBody DlpSelectionUpdate request,
            @AuthenticationPrincipal UserContext user) {
        requireAdmin(user);
        return ApiResponse.ok(configService.createSelection(request));
    }

    @PutMapping("/selections/{id}")
    @Operation(summary = "更新 DLP 生效范围")
    public ApiResponse<DlpSelectionConfig> updateSelection(
            @PathVariable String id,
            @RequestBody DlpSelectionUpdate request,
            @AuthenticationPrincipal UserContext user) {
        requireAdmin(user);
        return ApiResponse.ok(configService.updateSelection(id, request));
    }

    @DeleteMapping("/selections/{id}")
    @Operation(summary = "删除 DLP 生效范围")
    public ApiResponse<Void> deleteSelection(
            @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        requireAdmin(user);
        configService.deleteSelection(id);
        return ApiResponse.ok();
    }

    private void requireAdmin(UserContext user) {
        if (user == null || !user.isAdmin()) {
            throw com.sealmail.app.exception.SecurityException.accessDenied("Only administrators can manage DLP");
        }
    }
}
