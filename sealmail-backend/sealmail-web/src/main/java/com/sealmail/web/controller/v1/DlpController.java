package com.sealmail.web.controller.v1;

import com.sealmail.app.dto.request.CreateDlpPatternRequest;
import com.sealmail.app.dto.request.CreateDlpSelectionRequest;
import com.sealmail.app.dto.request.DlpPolicyRequest;
import com.sealmail.app.dto.request.DlpRuleGroupRequest;
import com.sealmail.app.dto.request.DlpRuleRequest;
import com.sealmail.app.dto.request.DlpTestApiRequest;
import com.sealmail.app.dto.request.UpdateDlpPatternRequest;
import com.sealmail.app.dto.request.UpdateDlpSelectionRequest;
import com.sealmail.app.dto.common.PageRequest;
import com.sealmail.app.dto.common.PageResponse;
import com.sealmail.app.dto.response.DlpEvaluationResponse;
import com.sealmail.app.dto.response.DlpEventResponse;
import com.sealmail.app.dto.response.DlpEvidenceResponse;
import com.sealmail.app.dto.response.DlpPatternResponse;
import com.sealmail.app.dto.response.DlpPolicyResponse;
import com.sealmail.app.dto.response.DlpRuleGroupResponse;
import com.sealmail.app.dto.response.DlpRuleResponse;
import com.sealmail.app.dto.response.DlpSelectionResponse;
import com.sealmail.app.security.UserContext;
import com.sealmail.app.usecase.config.ManageDlpConfigUseCase;
import com.sealmail.app.usecase.dlp.DlpOperationsUseCase;
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
    private final DlpOperationsUseCase dlpOperationsUseCase;

    public DlpController(ManageDlpConfigUseCase manageDlpConfigUseCase,
                         DlpOperationsUseCase dlpOperationsUseCase) {
        this.manageDlpConfigUseCase = manageDlpConfigUseCase;
        this.dlpOperationsUseCase = dlpOperationsUseCase;
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

    @GetMapping("/rules")
    @Operation(summary = "查询 DLP 规则库")
    public ApiResponse<List<DlpRuleResponse>> listRules(@AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(manageDlpConfigUseCase.listRules(user));
    }

    @PostMapping("/rules")
    @Operation(summary = "创建 DLP 规则")
    public ApiResponse<DlpRuleResponse> createRule(
            @RequestBody DlpRuleRequest request,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(manageDlpConfigUseCase.createRule(request, user));
    }

    @PutMapping("/rules/{id}")
    @Operation(summary = "更新 DLP 规则")
    public ApiResponse<DlpRuleResponse> updateRule(
            @PathVariable String id,
            @RequestBody DlpRuleRequest request,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(manageDlpConfigUseCase.updateRule(id, request, user));
    }

    @DeleteMapping("/rules/{id}")
    @Operation(summary = "删除 DLP 规则")
    public ApiResponse<Void> deleteRule(
            @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        manageDlpConfigUseCase.deleteRule(id, user);
        return ApiResponse.ok();
    }

    @GetMapping("/rule-groups")
    @Operation(summary = "查询 DLP 规则组")
    public ApiResponse<List<DlpRuleGroupResponse>> listRuleGroups(@AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(manageDlpConfigUseCase.listRuleGroups(user));
    }

    @PostMapping("/rule-groups")
    @Operation(summary = "创建 DLP 规则组")
    public ApiResponse<DlpRuleGroupResponse> createRuleGroup(
            @RequestBody DlpRuleGroupRequest request,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(manageDlpConfigUseCase.createRuleGroup(request, user));
    }

    @PutMapping("/rule-groups/{id}")
    @Operation(summary = "更新 DLP 规则组")
    public ApiResponse<DlpRuleGroupResponse> updateRuleGroup(
            @PathVariable String id,
            @RequestBody DlpRuleGroupRequest request,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(manageDlpConfigUseCase.updateRuleGroup(id, request, user));
    }

    @DeleteMapping("/rule-groups/{id}")
    @Operation(summary = "删除 DLP 规则组")
    public ApiResponse<Void> deleteRuleGroup(
            @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        manageDlpConfigUseCase.deleteRuleGroup(id, user);
        return ApiResponse.ok();
    }

    @GetMapping("/policies")
    @Operation(summary = "查询 DLP 策略集")
    public ApiResponse<List<DlpPolicyResponse>> listPolicies(@AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(manageDlpConfigUseCase.listPolicies(user));
    }

    @PostMapping("/policies")
    @Operation(summary = "创建 DLP 策略")
    public ApiResponse<DlpPolicyResponse> createPolicy(
            @RequestBody DlpPolicyRequest request,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(manageDlpConfigUseCase.createPolicy(request, user));
    }

    @PutMapping("/policies/{id}")
    @Operation(summary = "更新 DLP 策略")
    public ApiResponse<DlpPolicyResponse> updatePolicy(
            @PathVariable String id,
            @RequestBody DlpPolicyRequest request,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(manageDlpConfigUseCase.updatePolicy(id, request, user));
    }

    @DeleteMapping("/policies/{id}")
    @Operation(summary = "删除 DLP 策略")
    public ApiResponse<Void> deletePolicy(
            @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        manageDlpConfigUseCase.deletePolicy(id, user);
        return ApiResponse.ok();
    }

    @PostMapping("/test")
    @Operation(summary = "测试 DLP 内容")
    public ApiResponse<DlpEvaluationResponse> test(
            @RequestBody DlpTestApiRequest request,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(dlpOperationsUseCase.test(request, user));
    }

    @PostMapping("/policies/{id}/simulate")
    @Operation(summary = "仿真 DLP 策略")
    public ApiResponse<DlpEvaluationResponse> simulatePolicy(
            @PathVariable String id,
            @RequestBody DlpTestApiRequest request,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(dlpOperationsUseCase.simulatePolicy(id, request, user));
    }

    @GetMapping("/events")
    @Operation(summary = "查询 DLP 命中事件")
    public ApiResponse<PageResponse<DlpEventResponse>> listEvents(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Integer minSeverity,
            @RequestParam(required = false) String rule,
            @RequestParam(required = false) String domain,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(dlpOperationsUseCase.listEvents(
                PageRequest.builder().page(page).size(size).build(),
                action,
                minSeverity,
                rule,
                domain,
                user));
    }

    @GetMapping("/events/{id}/evidence")
    @Operation(summary = "查询 DLP 事件证据")
    public ApiResponse<List<DlpEvidenceResponse>> eventEvidence(
            @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(dlpOperationsUseCase.eventEvidence(id, user));
    }
}
