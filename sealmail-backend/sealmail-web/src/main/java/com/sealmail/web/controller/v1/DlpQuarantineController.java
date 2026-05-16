package com.sealmail.web.controller.v1;

import com.sealmail.app.dto.common.PageRequest;
import com.sealmail.app.dto.common.PageResponse;
import com.sealmail.app.dto.request.RejectQuarantineRequest;
import com.sealmail.app.dto.request.RepairQuarantineReleaseRequest;
import com.sealmail.app.dto.request.ReleaseQuarantineRequest;
import com.sealmail.app.dto.response.QuarantineItemResponse;
import com.sealmail.app.dto.response.QuarantineStatsResponse;
import com.sealmail.app.security.UserContext;
import com.sealmail.app.usecase.quarantine.QueryQuarantineUseCase;
import com.sealmail.app.usecase.quarantine.RejectQuarantineUseCase;
import com.sealmail.app.usecase.quarantine.RepairQuarantineReleaseUseCase;
import com.sealmail.app.usecase.quarantine.ReleaseQuarantineUseCase;
import com.sealmail.web.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dlp/quarantine")
@RequiredArgsConstructor
@Tag(name = "DLP 隔离", description = "DLP 命中 QUARANTINE 动作后的人工复核队列")
public class DlpQuarantineController {

    private final QueryQuarantineUseCase queryQuarantineUseCase;
    private final ReleaseQuarantineUseCase releaseQuarantineUseCase;
    private final RejectQuarantineUseCase rejectQuarantineUseCase;
    private final RepairQuarantineReleaseUseCase repairQuarantineReleaseUseCase;

    @GetMapping("/stats")
    @Operation(summary = "DLP 隔离队列统计")
    public ApiResponse<QuarantineStatsResponse> getStats(
            @AuthenticationPrincipal UserContext user) {

        return ApiResponse.ok(queryQuarantineUseCase.getStats(user));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询 DLP 隔离队列邮件详情")
    public ApiResponse<QuarantineItemResponse> findById(
            @Parameter(description = "DLP 隔离队列邮件 ID") @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {

        return ApiResponse.ok(queryQuarantineUseCase.findById(id, user));
    }

    @GetMapping
    @Operation(summary = "分页查询 DLP 隔离队列邮件")
    public ApiResponse<PageResponse<QuarantineItemResponse>> list(
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

        return ApiResponse.ok(queryQuarantineUseCase.findAll(pageRequest, reason, user));
    }

    @PostMapping("/{id}/release")
    @Operation(summary = "放行 DLP 隔离队列邮件")
    public ApiResponse<QuarantineItemResponse> release(
            @Parameter(description = "DLP 隔离队列邮件 ID") @PathVariable String id,
            @Valid @RequestBody(required = false) ReleaseQuarantineRequest request,
            @AuthenticationPrincipal UserContext user) {

        return ApiResponse.ok(releaseQuarantineUseCase.execute(
                id,
                request != null ? request : new ReleaseQuarantineRequest(),
                user));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "拒绝 DLP 隔离队列邮件")
    public ApiResponse<QuarantineItemResponse> reject(
            @Parameter(description = "DLP 隔离队列邮件 ID") @PathVariable String id,
            @RequestBody(required = false) RejectQuarantineRequest request,
            @AuthenticationPrincipal UserContext user) {

        return ApiResponse.ok(rejectQuarantineUseCase.execute(
                id,
                request != null ? request : new RejectQuarantineRequest(),
                user));
    }

    @PostMapping("/{id}/release/complete")
    @Operation(summary = "人工确认释放中邮件已投递")
    public ApiResponse<QuarantineItemResponse> completeRelease(
            @Parameter(description = "DLP 隔离队列邮件 ID") @PathVariable String id,
            @RequestBody(required = false) RepairQuarantineReleaseRequest request,
            @AuthenticationPrincipal UserContext user) {

        return ApiResponse.ok(repairQuarantineReleaseUseCase.complete(
                id,
                request != null ? request : new RepairQuarantineReleaseRequest(),
                user));
    }

    @PostMapping("/{id}/release/restore")
    @Operation(summary = "人工恢复释放中邮件为待处理")
    public ApiResponse<QuarantineItemResponse> restoreRelease(
            @Parameter(description = "DLP 隔离队列邮件 ID") @PathVariable String id,
            @RequestBody(required = false) RepairQuarantineReleaseRequest request,
            @AuthenticationPrincipal UserContext user) {

        return ApiResponse.ok(repairQuarantineReleaseUseCase.restore(
                id,
                request != null ? request : new RepairQuarantineReleaseRequest(),
                user));
    }

    @PostMapping("/batch-release")
    @Operation(summary = "批量放行 DLP 隔离队列邮件")
    public ApiResponse<Void> batchRelease(
            @RequestBody List<String> ids,
            @AuthenticationPrincipal UserContext user) {

        for (String id : ids) {
            releaseQuarantineUseCase.execute(id, new ReleaseQuarantineRequest(), user);
        }
        return ApiResponse.ok();
    }

    @PostMapping("/batch-reject")
    @Operation(summary = "批量拒绝 DLP 隔离队列邮件")
    public ApiResponse<Void> batchReject(
            @RequestBody List<String> ids,
            @AuthenticationPrincipal UserContext user) {

        RejectQuarantineRequest request = new RejectQuarantineRequest();
        for (String id : ids) {
            rejectQuarantineUseCase.execute(id, request, user);
        }
        return ApiResponse.ok();
    }
}
