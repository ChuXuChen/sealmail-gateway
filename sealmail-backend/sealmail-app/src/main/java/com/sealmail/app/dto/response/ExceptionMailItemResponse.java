package com.sealmail.app.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionMailItemResponse {

    private String id;
    private String messageId;
    private String subject;
    private String sender;
    private List<String> recipients;
    private String direction;
    private String remoteAddress;
    private String reason;
    private String detail;
    private String blockedBy;
    private String blockComment;
    private Instant createdAt;
}
