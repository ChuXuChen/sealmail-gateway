package com.sealmail.web.controller.v1;

import com.sealmail.app.dto.request.GmEdgePolicyRequest;
import com.sealmail.app.dto.request.QuarantinePolicyRequest;
import com.sealmail.app.dto.request.RelayPolicyRequest;
import com.sealmail.app.dto.request.SmimeSuitePolicyRequest;
import com.sealmail.app.dto.response.GmEdgePolicyResponse;
import com.sealmail.app.dto.response.QuarantinePolicyResponse;
import com.sealmail.app.dto.response.RelayPolicyResponse;
import com.sealmail.app.dto.response.SmimeSuitePolicyResponse;
import com.sealmail.app.security.UserContext;
import com.sealmail.app.usecase.config.ManageRuntimePolicyUseCase;
import com.sealmail.web.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/runtime-policies")
@Tag(name = "运行时策略", description = "Relay 与隔离运行时业务配置")
public class RuntimePolicyController {

    private final ManageRuntimePolicyUseCase manageRuntimePolicyUseCase;

    public RuntimePolicyController(ManageRuntimePolicyUseCase manageRuntimePolicyUseCase) {
        this.manageRuntimePolicyUseCase = manageRuntimePolicyUseCase;
    }

    @GetMapping("/relay")
    @Operation(summary = "查询 Relay 策略")
    public ApiResponse<RelayPolicyResponse> relay(@AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(manageRuntimePolicyUseCase.getRelayPolicy(user));
    }

    @PutMapping("/relay")
    @Operation(summary = "更新 Relay 策略")
    public ApiResponse<RelayPolicyResponse> updateRelay(
            @RequestBody RelayPolicyRequest request,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(manageRuntimePolicyUseCase.updateRelayPolicy(request, user));
    }

    @GetMapping("/quarantine")
    @Operation(summary = "查询隔离策略")
    public ApiResponse<QuarantinePolicyResponse> quarantine(@AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(manageRuntimePolicyUseCase.getQuarantinePolicy(user));
    }

    @PutMapping("/quarantine")
    @Operation(summary = "更新隔离策略")
    public ApiResponse<QuarantinePolicyResponse> updateQuarantine(
            @RequestBody QuarantinePolicyRequest request,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(manageRuntimePolicyUseCase.updateQuarantinePolicy(request, user));
    }

    @GetMapping("/gm-edge")
    @Operation(summary = "查询国密 Edge 策略")
    public ApiResponse<GmEdgePolicyResponse> gmEdge(@AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(manageRuntimePolicyUseCase.getGmEdgePolicy(user));
    }

    @PutMapping("/gm-edge")
    @Operation(summary = "更新国密 Edge 策略")
    public ApiResponse<GmEdgePolicyResponse> updateGmEdge(
            @RequestBody GmEdgePolicyRequest request,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(manageRuntimePolicyUseCase.updateGmEdgePolicy(request, user));
    }

    @GetMapping("/smime-suite")
    @Operation(summary = "查询 S/MIME 套件策略")
    public ApiResponse<SmimeSuitePolicyResponse> smimeSuite(@AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(manageRuntimePolicyUseCase.getSmimeSuitePolicy(user));
    }

    @PutMapping("/smime-suite")
    @Operation(summary = "更新 S/MIME 套件策略")
    public ApiResponse<SmimeSuitePolicyResponse> updateSmimeSuite(
            @RequestBody SmimeSuitePolicyRequest request,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(manageRuntimePolicyUseCase.updateSmimeSuitePolicy(request, user));
    }
}
