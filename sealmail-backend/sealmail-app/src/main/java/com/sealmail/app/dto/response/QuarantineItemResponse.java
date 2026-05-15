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
public class QuarantineItemResponse {

    private String id;
    private String messageId;
    private String subject;
    private String sender;
    private List<String> recipients;
    private String direction;
    private String remoteAddress;

    private String reason;
    private String detail;
    private String status;
    private boolean hasRawContent;
    private boolean canRelease;
    private String releaseUnavailableReason;

    private Instant quarantinedAt;
    private Instant resolvedAt;
    private String resolvedBy;
    private String resolutionComment;
}
