package com.sealmail.app.usecase.quarantine;

import com.sealmail.domain.config.QuarantinePolicyPort;
import com.sealmail.domain.quarantine.QuarantineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class CleanupQuarantineRetentionUseCase {

    private final QuarantinePolicyPort quarantinePolicyPort;
    private final QuarantineRepository quarantineRepository;

    public CleanupQuarantineRetentionUseCase(QuarantinePolicyPort quarantinePolicyPort,
                                             QuarantineRepository quarantineRepository) {
        this.quarantinePolicyPort = quarantinePolicyPort;
        this.quarantineRepository = quarantineRepository;
    }

    @Transactional
    public QuarantineRetentionCleanupResult execute() {
        QuarantinePolicyPort.QuarantinePolicySettings settings = quarantinePolicyPort.getSettings();
        Instant cutoff = Instant.now().minus(Duration.ofDays(settings.maxRetentionDays()));
        int deletedCount = quarantineRepository.deleteCreatedBefore(cutoff);
        return new QuarantineRetentionCleanupResult(
                settings.maxRetentionDays(),
                cutoff,
                deletedCount);
    }

    public record QuarantineRetentionCleanupResult(
            int maxRetentionDays,
            Instant cutoff,
            int deletedCount
    ) {
    }
}
