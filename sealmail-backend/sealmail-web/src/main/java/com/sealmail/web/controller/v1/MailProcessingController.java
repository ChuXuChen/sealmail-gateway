package com.sealmail.web.controller.v1;

import com.sealmail.app.dto.common.PageRequest;
import com.sealmail.app.dto.common.PageResponse;
import com.sealmail.app.dto.response.AuditLogResponse;
import com.sealmail.app.dto.response.MailProcessingResponse;
import com.sealmail.app.security.UserContext;
import com.sealmail.app.usecase.mail.QueryMailProcessingUseCase;
import com.sealmail.web.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mail-processing")
@RequiredArgsConstructor
@Tag(name = "邮件处理状态", description = "统一查询单封邮件的认证、证书、加密、DLP、隔离和投递状态链")
public class MailProcessingController {

    private final QueryMailProcessingUseCase queryMailProcessingUseCase;

    @GetMapping
    @Operation(summary = "查询最近邮件处理状态")
    public ApiResponse<PageResponse<MailProcessingResponse>> listRecent(
            @Parameter(description = "页码（从 1 开始）")
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页大小")
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal UserContext user) {

        PageRequest pageRequest = PageRequest.builder()
                .page(page)
                .size(size)
                .build();

        return ApiResponse.ok(queryMailProcessingUseCase.listRecent(pageRequest, user));
    }

    @GetMapping("/{processingId}")
    @Operation(summary = "查询单封邮件处理状态")
    public ApiResponse<MailProcessingResponse> findById(
            @Parameter(description = "邮件处理 ID") @PathVariable String processingId,
            @AuthenticationPrincipal UserContext user) {

        return ApiResponse.ok(queryMailProcessingUseCase.findById(processingId, user));
    }

    @GetMapping("/{processingId}/audit-trace")
    @Operation(summary = "查询单封邮件审计轨迹")
    public ApiResponse<PageResponse<AuditLogResponse>> auditTrace(
            @Parameter(description = "邮件处理 ID") @PathVariable String processingId,
            @Parameter(description = "页码（从 1 开始）")
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页大小")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal UserContext user) {

        PageRequest pageRequest = PageRequest.builder()
                .page(page)
                .size(size)
                .build();

        return ApiResponse.ok(queryMailProcessingUseCase.auditTrace(processingId, pageRequest, user));
    }
}
