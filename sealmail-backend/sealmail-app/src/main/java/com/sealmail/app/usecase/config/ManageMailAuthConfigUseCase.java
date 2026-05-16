package com.sealmail.app.usecase.config;

import com.sealmail.app.dto.request.MailAuthConfigRequest;
import com.sealmail.app.dto.response.DnsRecordResponse;
import com.sealmail.app.dto.response.MailAuthConfigResponse;
import com.sealmail.app.dto.response.MailAuthStatusResponse;
import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.mailauth.MailAuthConfigPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ManageMailAuthConfigUseCase {

    private final MailAuthConfigPort mailAuthConfigPort;

    public ManageMailAuthConfigUseCase(MailAuthConfigPort mailAuthConfigPort) {
        this.mailAuthConfigPort = mailAuthConfigPort;
    }

    @Transactional(readOnly = true)
    public MailAuthConfigResponse getConfig(UserContext user) {
        requireAdmin(user, "只有管理员可以查看邮件认证配置");
        return toResponse(mailAuthConfigPort.getSettings());
    }

    @Transactional
    public MailAuthConfigResponse updateConfig(MailAuthConfigRequest request, UserContext user) {
        requireAdmin(user, "只有管理员可以更新邮件认证配置");
        return toResponse(mailAuthConfigPort.updateSettings(toUpdate(request)));
    }

    @Transactional(readOnly = true)
    public List<DnsRecordResponse> dnsRecords(String domain, UserContext user) {
        requireAdmin(user, "只有管理员可以查看邮件认证DNS记录");
        return mailAuthConfigPort.dnsRecordSettings(domain).stream()
                .map(record -> new DnsRecordResponse(
                        record.type(),
                        record.name(),
                        record.value(),
                        record.available()))
                .toList();
    }

    @Transactional(readOnly = true)
    public MailAuthStatusResponse status(UserContext user) {
        requireAdmin(user, "只有管理员可以查看邮件认证状态");
        return toStatus(mailAuthConfigPort.getSettings());
    }

    @Transactional
    public MailAuthStatusResponse updateStatus(StatusUpdateRequest request, UserContext user) {
        requireAdmin(user, "只有管理员可以更新邮件认证状态");
        MailAuthConfigPort.MailAuthSettings settings = mailAuthConfigPort.updateSettings(
                new MailAuthConfigPort.MailAuthSettingsUpdate(
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
        return toStatus(settings);
    }

    private void requireAdmin(UserContext user, String message) {
        if (user == null || !user.isAdmin()) {
            throw BusinessException.forbidden(message);
        }
    }

    private MailAuthConfigPort.MailAuthSettingsUpdate toUpdate(MailAuthConfigRequest request) {
        return new MailAuthConfigPort.MailAuthSettingsUpdate(
                request.enabled(),
                request.authservId(),
                request.skipPrivateRelay(),
                request.dkimEnabled(),
                request.dkimSelector(),
                request.dkimPrivateKeyPath(),
                request.dkimPrivateKeySecretRef(),
                request.clearDkimPrivateKeySecretRef(),
                request.dkimSignedHeaders(),
                request.spfEnabled(),
                request.spfMaxDnsLookups(),
                request.spfUseA(),
                request.spfUseMx(),
                request.spfIp4(),
                request.spfIp6(),
                request.spfIncludes(),
                request.spfAllPolicy(),
                request.dmarcEnabled(),
                request.dmarcPolicy(),
                request.dmarcAdkim(),
                request.dmarcAspf(),
                request.dmarcPct(),
                request.dmarcRua(),
                request.dmarcRuf(),
                request.dmarcFailureAction(),
                request.dmarcQuarantineRejectPolicy()
        );
    }

    private MailAuthConfigResponse toResponse(MailAuthConfigPort.MailAuthSettings settings) {
        return new MailAuthConfigResponse(
                settings.enabled(),
                settings.authservId(),
                settings.skipPrivateRelay(),
                settings.dkimEnabled(),
                settings.dkimSelector(),
                settings.dkimPrivateKeyPath(),
                settings.dkimPrivateKeySecretRef(),
                settings.dkimPrivateKeyConfigured(),
                settings.dkimSignedHeaders(),
                settings.spfEnabled(),
                settings.spfMaxDnsLookups(),
                settings.spfUseA(),
                settings.spfUseMx(),
                settings.spfIp4(),
                settings.spfIp6(),
                settings.spfIncludes(),
                settings.spfAllPolicy(),
                settings.dmarcEnabled(),
                settings.dmarcPolicy(),
                settings.dmarcAdkim(),
                settings.dmarcAspf(),
                settings.dmarcPct(),
                settings.dmarcRua(),
                settings.dmarcRuf(),
                settings.dmarcFailureAction(),
                settings.dmarcQuarantineRejectPolicy(),
                settings.updatedAt()
        );
    }

    private MailAuthStatusResponse toStatus(MailAuthConfigPort.MailAuthSettings settings) {
        return new MailAuthStatusResponse(
                settings.enabled(),
                settings.authservId(),
                settings.dkimEnabled(),
                settings.dkimSelector(),
                settings.spfEnabled(),
                settings.dmarcEnabled(),
                settings.dmarcQuarantineRejectPolicy(),
                settings.skipPrivateRelay()
        );
    }

    public record StatusUpdateRequest(
            Boolean enabled,
            Boolean dkimEnabled,
            Boolean spfEnabled,
            Boolean dmarcEnabled,
            Boolean dmarcQuarantineRejectPolicy,
            Boolean skipPrivateRelay
    ) {
    }
}
