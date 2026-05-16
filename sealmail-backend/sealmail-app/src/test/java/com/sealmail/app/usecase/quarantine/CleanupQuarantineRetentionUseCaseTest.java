package com.sealmail.app.usecase.quarantine;

import com.sealmail.domain.config.QuarantinePolicyPort;
import com.sealmail.domain.quarantine.QuarantineRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CleanupQuarantineRetentionUseCaseTest {

    @Test
    void deletesQuarantineRecordsOlderThanRuntimeRetentionPolicy() {
        QuarantinePolicyPort quarantinePolicyPort = mock(QuarantinePolicyPort.class);
        QuarantineRepository quarantineRepository = mock(QuarantineRepository.class);
        when(quarantinePolicyPort.getSettings()).thenReturn(new QuarantinePolicyPort.QuarantinePolicySettings(
                7,
                false,
                false,
                Instant.now()));
        when(quarantineRepository.deleteCreatedBefore(org.mockito.ArgumentMatchers.any())).thenReturn(3);
        CleanupQuarantineRetentionUseCase useCase = new CleanupQuarantineRetentionUseCase(
                quarantinePolicyPort,
                quarantineRepository);

        Instant before = Instant.now().minusSeconds(7L * 24 * 60 * 60 + 5);
        CleanupQuarantineRetentionUseCase.QuarantineRetentionCleanupResult result = useCase.execute();
        Instant after = Instant.now().minusSeconds(7L * 24 * 60 * 60 - 5);

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(quarantineRepository).deleteCreatedBefore(cutoffCaptor.capture());
        assertFalse(cutoffCaptor.getValue().isBefore(before));
        assertFalse(cutoffCaptor.getValue().isAfter(after));
        assertEquals(7, result.maxRetentionDays());
        assertEquals(3, result.deletedCount());
    }
}
