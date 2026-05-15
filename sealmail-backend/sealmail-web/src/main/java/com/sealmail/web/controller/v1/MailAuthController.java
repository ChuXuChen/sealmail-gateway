package com.sealmail.web.controller.v1;

import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.security.UserContext;
import com.sealmail.infra.mail.auth.config.DnsRecordResponse;
import com.sealmail.infra.mail.auth.config.MailAuthConfig;
import com.sealmail.infra.mail.auth.config.MailAuthConfigService;
import com.sealmail.infra.mail.auth.config.MailAuthConfigUpdate;
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

    private final MailAuthConfigService configService;

    public MailAuthController(MailAuthConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/config")
    @Operation(summary = "查询邮件认证配置")
    public ApiResponse<MailAuthConfig> config(@AuthenticationPrincipal UserContext user) {
        requireAdmin(user, "只有管理员可以查看邮件认证配置");
        return ApiResponse.ok(configService.getConfig());
    }

    @PutMapping("/config")
    @Operation(summary = "更新邮件认证配置")
    public ApiResponse<MailAuthConfig> updateConfig(
            @RequestBody MailAuthConfigUpdate request,
            @AuthenticationPrincipal UserContext user) {
        requireAdmin(user, "只有管理员可以更新邮件认证配置");
        return ApiResponse.ok(configService.updateConfig(request));
    }

    @GetMapping("/dns-records")
    @Operation(summary = "生成邮件认证DNS记录")
    public ApiResponse<List<DnsRecordResponse>> dnsRecords(
            @RequestParam String domain,
            @AuthenticationPrincipal UserContext user) {
        requireAdmin(user, "只有管理员可以查看邮件认证DNS记录");
        return ApiResponse.ok(configService.dnsRecords(domain));
    }

    @GetMapping("/status")
    @Operation(summary = "查询邮件认证运行状态")
    public ApiResponse<MailAuthStatusResponse> status(@AuthenticationPrincipal UserContext user) {
        requireAdmin(user, "只有管理员可以查看邮件认证状态");
        return ApiResponse.ok(statusResponse(configService.getConfig()));
    }

    @PutMapping("/status")
    @Operation(summary = "更新邮件认证运行开关")
    public ApiResponse<MailAuthStatusResponse> updateStatus(
            @RequestBody UpdateMailAuthStatusRequest request,
            @AuthenticationPrincipal UserContext user) {
        requireAdmin(user, "只有管理员可以更新邮件认证状态");
        MailAuthConfig config = configService.updateConfig(new MailAuthConfigUpdate(
                request.enabled(),
                null,
                request.skipPrivateRelay(),
                request.dkimEnabled(),
                null,
                null,
                null,
                null,
                null,
                request.spfEnabled(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                request.dmarcEnabled(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                request.dmarcQuarantineRejectPolicy()
        ));
        return ApiResponse.ok(statusResponse(config));
    }

    private void requireAdmin(UserContext user, String message) {
        if (user == null || !user.isAdmin()) {
            throw BusinessException.forbidden(message);
        }
    }

    private MailAuthStatusResponse statusResponse(MailAuthConfig config) {
        return new MailAuthStatusResponse(
                config.enabled(),
                config.authservId(),
                config.dkimEnabled(),
                config.dkimSelector(),
                config.spfEnabled(),
                config.dmarcEnabled(),
                config.dmarcQuarantineRejectPolicy(),
                config.skipPrivateRelay()
        );
    }

    public record MailAuthStatusResponse(
            boolean enabled,
            String authservId,
            boolean dkimEnabled,
            String dkimSelector,
            boolean spfEnabled,
            boolean dmarcEnabled,
            boolean dmarcQuarantineRejectPolicy,
            boolean skipPrivateRelay
    ) {
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
