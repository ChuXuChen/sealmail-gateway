package com.sealmail.web.controller.v1;

import com.sealmail.app.dto.request.CreateDlpPatternRequest;
import com.sealmail.app.dto.request.CreateDlpSelectionRequest;
import com.sealmail.app.dto.request.UpdateDlpPatternRequest;
import com.sealmail.app.dto.request.UpdateDlpSelectionRequest;
import com.sealmail.app.dto.response.DlpPatternResponse;
import com.sealmail.app.dto.response.DlpSelectionResponse;
import com.sealmail.app.security.UserContext;
import com.sealmail.app.usecase.config.ManageDlpConfigUseCase;
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

    private final ManageDlpConfigUseCase manageDlpConfigUseCase;

    public DlpController(ManageDlpConfigUseCase manageDlpConfigUseCase) {
        this.manageDlpConfigUseCase = manageDlpConfigUseCase;
    }

    @GetMapping("/patterns")
    @Operation(summary = "查询 DLP 规则")
    public ApiResponse<List<DlpPatternResponse>> listPatterns(@AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(manageDlpConfigUseCase.listPatterns(user));
    }

    @PostMapping("/patterns")
    @Operation(summary = "创建 DLP 规则")
    public ApiResponse<DlpPatternResponse> createPattern(
            @RequestBody CreateDlpPatternRequest request,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(manageDlpConfigUseCase.createPattern(request, user));
    }

    @PutMapping("/patterns/{id}")
    @Operation(summary = "更新 DLP 规则")
    public ApiResponse<DlpPatternResponse> updatePattern(
            @PathVariable String id,
            @RequestBody UpdateDlpPatternRequest request,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(manageDlpConfigUseCase.updatePattern(id, request, user));
    }

    @DeleteMapping("/patterns/{id}")
    @Operation(summary = "删除 DLP 规则")
    public ApiResponse<Void> deletePattern(
            @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        manageDlpConfigUseCase.deletePattern(id, user);
        return ApiResponse.ok();
    }

    @GetMapping("/selections")
    @Operation(summary = "查询 DLP 生效范围")
    public ApiResponse<List<DlpSelectionResponse>> listSelections(@AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(manageDlpConfigUseCase.listSelections(user));
    }

    @PostMapping("/selections")
    @Operation(summary = "创建 DLP 生效范围")
    public ApiResponse<DlpSelectionResponse> createSelection(
            @RequestBody CreateDlpSelectionRequest request,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(manageDlpConfigUseCase.createSelection(request, user));
    }

    @PutMapping("/selections/{id}")
    @Operation(summary = "更新 DLP 生效范围")
    public ApiResponse<DlpSelectionResponse> updateSelection(
            @PathVariable String id,
            @RequestBody UpdateDlpSelectionRequest request,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(manageDlpConfigUseCase.updateSelection(id, request, user));
    }

    @DeleteMapping("/selections/{id}")
    @Operation(summary = "删除 DLP 生效范围")
    public ApiResponse<Void> deleteSelection(
            @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        manageDlpConfigUseCase.deleteSelection(id, user);
        return ApiResponse.ok();
    }
}
