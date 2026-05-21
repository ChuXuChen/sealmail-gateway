package com.sealmail.app.dto.response;

import com.sealmail.domain.mailsecurity.MailProcessingStatusSnapshot;
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
public class MailProcessingResponse {

    private String id;
    private String processingId;
    private String messageId;
    private String direction;
    private String sender;
    private List<String> recipients;
    private String remoteHost;
    private String helo;
    private Instant receivedAt;
    private String routingDecision;
    private String result;
    private String disposition;
    private String failedStep;
    private String failureReason;
    private MailProcessingStatusSnapshot statusSnapshot;
    private List<MailProcessingStepResponse> steps;
}
