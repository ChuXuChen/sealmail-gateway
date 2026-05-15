package com.sealmail.app.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuarantineStatsResponse {

    private long total;
    private long pending;
    private long released;
    private long rejected;
    private Map<String, Long> byReason;
}
