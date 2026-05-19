package com.sealmail.infra.dlp.scanner;

import com.sealmail.domain.policy.DispositionAction;
import com.sealmail.infra.dlp.config.DlpPatternConfig;
import com.sealmail.infra.dlp.config.DlpRuntimeConfigPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfiguredRegexDlpScannerTest {

    @Test
    void scansOnlyPatternsSelectedForEnvelope() {
        DlpRuntimeConfigPort configService = mock(DlpRuntimeConfigPort.class);
        when(configService.activePatternsFor(any())).thenReturn(List.of(
                pattern("block-rule", "BLOCK_TOKEN", DispositionAction.BLOCK)
        ));
        ConfiguredRegexDlpScanner scanner = new ConfiguredRegexDlpScanner(configService);

        var result = scanner.scan(
                "subject",
                "BLOCK_TOKEN QUARANTINE_TOKEN",
                null,
                null
        );

        assertEquals(1, result.getViolations().size());
        assertEquals("block-rule", result.getPrimaryViolation().orElseThrow().getRuleName());
        assertEquals(DispositionAction.BLOCK, result.getFinalAction());
        assertTrue(result.shouldBlock());
    }

    private DlpPatternConfig pattern(String name, String regex, DispositionAction action) {
        Instant now = Instant.now();
        return new DlpPatternConfig(
                "id-" + name,
                name,
                "description " + name,
                regex,
                action,
                5,
                100,
                true,
                now,
                now
        );
    }
}
