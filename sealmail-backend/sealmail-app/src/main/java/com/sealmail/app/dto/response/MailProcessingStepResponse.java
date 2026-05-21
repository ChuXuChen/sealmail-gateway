package com.sealmail.app.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailProcessingStepResponse {

    private String id;
    private String stepName;
    private boolean completed;
    private boolean success;
    private String errorMessage;
    private Instant startedAt;
    private Instant completedAt;
}
