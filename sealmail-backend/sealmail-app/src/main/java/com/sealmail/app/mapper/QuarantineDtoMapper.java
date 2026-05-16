package com.sealmail.app.mapper;

import com.sealmail.app.dto.response.QuarantineItemResponse;
import com.sealmail.app.dto.response.QuarantineStatsResponse;
import com.sealmail.domain.quarantine.QuarantineRepository;
import com.sealmail.domain.quarantine.QuarantineStatus;
import com.sealmail.domain.quarantine.QuarantinedMail;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class QuarantineDtoMapper {

    public QuarantineItemResponse toResponse(QuarantinedMail mail) {
        boolean hasRawContent = mail.hasRawContent();
        boolean canRelease = mail.getStatus() == QuarantineStatus.QUARANTINED
                && hasRawContent;

        return QuarantineItemResponse.builder()
                .id(mail.getId())
                .messageId(mail.getMessageId())
                .subject(mail.getSubject())
                .sender(mail.getSender().getValue())
                .recipients(mail.getRecipients().stream()
                        .map(email -> email.getValue())
                        .toList())
                .direction(mail.getDirection() != null ? mail.getDirection().name() : null)
                .remoteAddress(mail.getRemoteAddress())
                .reason(mail.getReason().name())
                .detail(mail.getDetail())
                .status(mail.getStatus().name())
                .hasRawContent(hasRawContent)
                .canRelease(canRelease)
                .releaseUnavailableReason(releaseUnavailableReason(mail, hasRawContent))
                .quarantinedAt(mail.getCreatedAt())
                .resolvedAt(mail.getResolvedAt())
                .resolvedBy(mail.getProcessedBy())
                .resolutionComment(mail.getProcessComment())
                .build();
    }

    private String releaseUnavailableReason(QuarantinedMail mail, boolean hasRawContent) {
        if (mail.getStatus() == QuarantineStatus.RELEASING) {
            return "邮件释放状态待人工确认";
        }
        if (mail.getStatus() == QuarantineStatus.RELEASED) {
            return "邮件已放行";
        }
        if (mail.getStatus() == QuarantineStatus.REJECTED) {
            return "邮件已拒绝";
        }
        if (!hasRawContent) {
            return "原始邮件内容缺失，无法重新投递";
        }
        return null;
    }

    public QuarantineStatsResponse toStatsResponse(QuarantineRepository repository) {
        Map<String, Long> byReason = new HashMap<>();
        // Stats calculation will be done by UseCase
        return QuarantineStatsResponse.builder()
                .total(repository.count())
                .pending(repository.countByStatus(QuarantineStatus.QUARANTINED))
                .releasing(repository.countByStatus(QuarantineStatus.RELEASING))
                .released(repository.countByStatus(QuarantineStatus.RELEASED))
                .rejected(repository.countByStatus(QuarantineStatus.REJECTED))
                .byReason(byReason)
                .build();
    }
}
