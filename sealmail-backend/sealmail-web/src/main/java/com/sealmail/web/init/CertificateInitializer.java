package com.sealmail.web.init;

import com.sealmail.app.dto.request.CreateIntermediateCaRequest;
import com.sealmail.app.dto.request.CreateRootCaRequest;
import com.sealmail.app.dto.request.IssueEndEntityRequest;
import com.sealmail.app.dto.response.CertificateResponse;
import com.sealmail.app.security.UserContext;
import com.sealmail.app.usecase.certificate.CreateIntermediateCaUseCase;
import com.sealmail.app.usecase.certificate.CreateRootCaUseCase;
import com.sealmail.app.usecase.certificate.IssueEndEntityUseCase;
import com.sealmail.domain.certificate.CertificateRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * On startup, plant a complete demo PKI hierarchy if the certificate store is empty:
 * <pre>
 *   RSA-Root         (Root CA, pathLen=1)
 *     └─ RSA-Intermediate    (Intermediate CA, pathLen=0)
 *         ├─ sender   (end-entity, RSA, emailProtection)
 *         └─ recipient (end-entity, RSA, emailProtection)
 *   SM2-Root         (Root CA, pathLen=1)
 *     └─ SM2-Intermediate    (Intermediate CA, pathLen=0)
 *         ├─ sender   (end-entity, SM2, emailProtection)
 *         └─ recipient (end-entity, SM2, emailProtection)
 * </pre>
 *
 * If anything exists in {@code certificate} table this initializer skips. Truncate
 * the table to re-seed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CertificateInitializer {

    private final CertificateRepository certificateRepository;
    private final CreateRootCaUseCase createRootCaUseCase;
    private final CreateIntermediateCaUseCase createIntermediateCaUseCase;
    private final IssueEndEntityUseCase issueEndEntityUseCase;

    @Value("${sealmail.init.certs.enabled:true}")
    private boolean initCertsEnabled;

    @PostConstruct
    public void init() {
        if (!initCertsEnabled) return;

        if (!certificateRepository.findAll().isEmpty()) {
            log.info("Certificates already initialized, skipping seed.");
            return;
        }

        log.info("Seeding internal CA hierarchy (RSA + SM2 双栈)...");

        UserContext system = UserContext.builder()
                .userId("system")
                .email("system@sealmail.local")
                .roles(java.util.Set.of("PKI_ADMIN"))
                .build();

        for (String algo : List.of("RSA", "SM2")) {
            CertificateResponse root = createRootCaUseCase.execute(
                    CreateRootCaRequest.builder()
                            .algorithm(algo)
                            .commonName("SealMail " + algo + " Root CA")
                            .alias(algo + " Root CA")
                            .build(),
                    system);
            log.info("  seeded Root CA ({}): {}", algo, root.getId().substring(0, 16) + "…");

            CertificateResponse intermediate = createIntermediateCaUseCase.execute(
                    CreateIntermediateCaRequest.builder()
                            .rootCaId(root.getId())
                            .algorithm(algo)
                            .commonName("SealMail " + algo + " Intermediate CA")
                            .alias(algo + " Intermediate CA")
                            .build(),
                    system);
            log.info("  seeded Intermediate CA ({}): {}", algo, intermediate.getId().substring(0, 16) + "…");

            for (String owner : List.of("cxc1234567892022@163.com", "2416507029@qq.com")) {
                CertificateResponse ee = issueEndEntityUseCase.execute(
                        IssueEndEntityRequest.builder()
                                .intermediateCaId(intermediate.getId())
                                .ownerEmail(owner)
                                .algorithm(algo)
                                .alias(owner.split("@")[0] + "-" + algo)
                                .trusted(Boolean.TRUE)
                                .build(),
                        system);
                log.info("    end-entity {} ({}): {}", owner, algo, ee.getId().substring(0, 16) + "…");
            }
        }
        log.info("Seed complete: 2 Roots + 2 Intermediates + 4 end-entities.");
    }
}
