package com.sealmail.web.controller.v1;

import com.sealmail.app.dto.common.PageRequest;
import com.sealmail.app.dto.common.PageResponse;
import com.sealmail.app.dto.response.ExceptionMailItemResponse;
import com.sealmail.app.dto.response.ExceptionMailStatsResponse;
import com.sealmail.app.security.UserContext;
import com.sealmail.app.usecase.exceptionmail.QueryExceptionMailUseCase;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.web.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/exception-mails")
@RequiredArgsConstructor
@Tag(name = "异常邮件", description = "自动阻断或处理失败邮件记录，只可查询，不可放行")
public class ExceptionMailController {

    private final QueryExceptionMailUseCase queryExceptionMailUseCase;

    @GetMapping("/stats")
    @Operation(summary = "异常邮件统计", description = "获取自动阻断或处理失败邮件统计")
    public ApiResponse<ExceptionMailStatsResponse> getStats(
            @AuthenticationPrincipal UserContext user) {

        return ApiResponse.ok(queryExceptionMailUseCase.getStats(user));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询异常邮件详情", description = "根据 ID 获取异常邮件记录")
    public ApiResponse<ExceptionMailItemResponse> findById(
            @Parameter(description = "异常邮件 ID") @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {

        return ApiResponse.ok(queryExceptionMailUseCase.findById(id, user));
    }

    @GetMapping
    @Operation(summary = "分页查询异常邮件", description = "查询自动阻断或处理失败邮件记录")
    public ApiResponse<PageResponse<ExceptionMailItemResponse>> list(
            @Parameter(description = "页码（从 1 开始）")
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页大小")
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
            @Parameter(description = "原因类型")
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal UserContext user) {

        PageRequest pageRequest = PageRequest.builder()
                .page(page)
                .size(size)
                .build();

        QuarantineReason parsedReason = null;
        if (reason != null && !reason.isBlank()) {
            parsedReason = QuarantineReason.valueOf(reason);
        }

        return ApiResponse.ok(queryExceptionMailUseCase.findAll(pageRequest, parsedReason, user));
    }
}
