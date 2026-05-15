package com.sealmail.web.controller.v1;

import com.sealmail.app.dto.common.PageRequest;
import com.sealmail.app.dto.common.PageResponse;
import com.sealmail.app.dto.response.AuditLogResponse;
import com.sealmail.app.security.UserContext;
import com.sealmail.app.usecase.audit.QueryAuditLogUseCase;
import com.sealmail.web.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@Tag(name = "审计日志", description = "系统操作审计日志查询")
public class AuditLogController {

    private final QueryAuditLogUseCase queryAuditLogUseCase;

    @GetMapping
    @Operation(summary = "分页查询审计日志", description = "管理员和审计员可查看所有审计日志")
    public ApiResponse<PageResponse<AuditLogResponse>> listAll(
            @Parameter(description = "页码（从1开始）")
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页大小")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "事件分类")
            @RequestParam(required = false) String category,
            @Parameter(description = "日志类型")
            @RequestParam(required = false) String type,
            @Parameter(description = "是否成功")
            @RequestParam(required = false) Boolean success,
            @AuthenticationPrincipal UserContext user) {

        PageRequest pageRequest = PageRequest.builder().page(page).size(size).build();
        PageResponse<AuditLogResponse> result =
                queryAuditLogUseCase.search(category, type, success, pageRequest, user);
        return ApiResponse.ok(result);
    }

    @GetMapping("/type/{type}")
    @Operation(summary = "按类型查询审计日志", description = "按操作类型筛选审计日志")
    public ApiResponse<PageResponse<AuditLogResponse>> findByType(
            @Parameter(description = "日志类型") @PathVariable String type,
            @Parameter(description = "页码（从1开始）")
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页大小")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal UserContext user) {

        PageRequest pageRequest = PageRequest.builder().page(page).size(size).build();
        PageResponse<AuditLogResponse> result = queryAuditLogUseCase.findByType(type, pageRequest, user);
        return ApiResponse.ok(result);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "按用户查询审计日志", description = "查看特定用户的操作日志")
    public ApiResponse<PageResponse<AuditLogResponse>> findByUserId(
            @Parameter(description = "用户ID") @PathVariable String userId,
            @Parameter(description = "页码（从1开始）")
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页大小")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal UserContext user) {

        PageRequest pageRequest = PageRequest.builder().page(page).size(size).build();
        PageResponse<AuditLogResponse> result = queryAuditLogUseCase.findByUserId(userId, pageRequest, user);
        return ApiResponse.ok(result);
    }

    @GetMapping("/time-range")
    @Operation(summary = "按时间范围查询审计日志", description = "按起止时间筛选审计日志")
    public ApiResponse<PageResponse<AuditLogResponse>> findByTimeRange(
            @Parameter(description = "开始时间（ISO格式）") @RequestParam String startTime,
            @Parameter(description = "结束时间（ISO格式）") @RequestParam String endTime,
            @Parameter(description = "页码（从1开始）")
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页大小")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal UserContext user) {

        Instant start = Instant.parse(startTime);
        Instant end = Instant.parse(endTime);
        PageRequest pageRequest = PageRequest.builder().page(page).size(size).build();
        PageResponse<AuditLogResponse> result = queryAuditLogUseCase.findByTimeRange(start, end, pageRequest, user);
        return ApiResponse.ok(result);
    }
}
