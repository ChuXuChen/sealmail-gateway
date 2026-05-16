package com.sealmail.boot.schedule;

import com.sealmail.app.usecase.quarantine.CleanupQuarantineRetentionUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class QuarantineRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(QuarantineRetentionScheduler.class);

    private final CleanupQuarantineRetentionUseCase cleanupQuarantineRetentionUseCase;

    public QuarantineRetentionScheduler(CleanupQuarantineRetentionUseCase cleanupQuarantineRetentionUseCase) {
        this.cleanupQuarantineRetentionUseCase = cleanupQuarantineRetentionUseCase;
    }

    @Scheduled(cron = "${sealmail.quarantine-retention.cleanup-cron:0 0 * * * *}")
    public void cleanupExpiredQuarantineMail() {
        CleanupQuarantineRetentionUseCase.QuarantineRetentionCleanupResult result =
                cleanupQuarantineRetentionUseCase.execute();
        if (result.deletedCount() > 0) {
            log.info("Deleted {} quarantined mails older than {} days before {}",
                    result.deletedCount(),
                    result.maxRetentionDays(),
                    result.cutoff());
        }
    }
}
