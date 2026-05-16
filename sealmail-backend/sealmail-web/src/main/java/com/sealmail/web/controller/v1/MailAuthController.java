package com.sealmail.web.controller.v1;

import com.sealmail.app.dto.request.MailAuthConfigRequest;
import com.sealmail.app.dto.response.DnsRecordResponse;
import com.sealmail.app.dto.response.MailAuthConfigResponse;
import com.sealmail.app.dto.response.MailAuthStatusResponse;
import com.sealmail.app.security.UserContext;
import com.sealmail.app.usecase.config.ManageMailAuthConfigUseCase;
import com.sealmail.web.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mail-auth")
@Tag(name = "邮件认证", description = "DKIM、SPF、DMARC配置")
public class MailAuthController {

    private final ManageMailAuthConfigUseCase manageMailAuthConfigUseCase;

    public MailAuthController(ManageMailAuthConfigUseCase manageMailAuthConfigUseCase) {
        this.manageMailAuthConfigUseCase = manageMailAuthConfigUseCase;
    }

    @GetMapping("/config")
    @Operation(summary = "查询邮件认证配置")
    public ApiResponse<MailAuthConfigResponse> config(@AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(manageMailAuthConfigUseCase.getConfig(user));
    }

    @PutMapping("/config")
    @Operation(summary = "更新邮件认证配置")
    public ApiResponse<MailAuthConfigResponse> updateConfig(
            @RequestBody MailAuthConfigRequest request,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(manageMailAuthConfigUseCase.updateConfig(request, user));
    }

    @GetMapping("/dns-records")
    @Operation(summary = "生成邮件认证DNS记录")
    public ApiResponse<List<DnsRecordResponse>> dnsRecords(
            @RequestParam String domain,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(manageMailAuthConfigUseCase.dnsRecords(domain, user));
    }

    @GetMapping("/status")
    @Operation(summary = "查询邮件认证运行状态")
    public ApiResponse<MailAuthStatusResponse> status(@AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(manageMailAuthConfigUseCase.status(user));
    }

    @PutMapping("/status")
    @Operation(summary = "更新邮件认证运行开关")
    public ApiResponse<MailAuthStatusResponse> updateStatus(
            @RequestBody UpdateMailAuthStatusRequest request,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(manageMailAuthConfigUseCase.updateStatus(
                new ManageMailAuthConfigUseCase.StatusUpdateRequest(
                request.enabled(),
                request.dkimEnabled(),
                request.spfEnabled(),
                request.dmarcEnabled(),
                request.dmarcQuarantineRejectPolicy(),
                request.skipPrivateRelay()
        ), user));
    }

    public record UpdateMailAuthStatusRequest(
            Boolean enabled,
            Boolean dkimEnabled,
            Boolean spfEnabled,
            Boolean dmarcEnabled,
            Boolean dmarcQuarantineRejectPolicy,
            Boolean skipPrivateRelay
    ) {
    }
}
