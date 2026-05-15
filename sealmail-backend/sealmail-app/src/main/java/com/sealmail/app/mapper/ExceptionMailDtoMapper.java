package com.sealmail.app.mapper;

import com.sealmail.app.dto.response.ExceptionMailItemResponse;
import com.sealmail.domain.exceptionmail.ExceptionMail;
import org.springframework.stereotype.Component;

@Component
public class ExceptionMailDtoMapper {

    public ExceptionMailItemResponse toResponse(ExceptionMail mail) {
        return ExceptionMailItemResponse.builder()
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
                .blockedBy(mail.getBlockedBy())
                .blockComment(mail.getBlockComment())
                .createdAt(mail.getCreatedAt())
                .build();
    }
}
