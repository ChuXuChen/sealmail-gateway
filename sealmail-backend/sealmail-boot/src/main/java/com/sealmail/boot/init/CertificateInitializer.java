package com.sealmail.boot.init;

import com.sealmail.app.dto.request.CreateIntermediateCaRequest;
import com.sealmail.app.dto.request.CreateRootCaRequest;
import com.sealmail.app.dto.request.IssueEndEntityRequest;
import com.sealmail.app.dto.response.CertificateResponse;
import com.sealmail.app.security.UserContext;
import com.sealmail.app.usecase.bootstrap.CertificateStoreStatusUseCase;
import com.sealmail.app.usecase.certificate.CreateIntermediateCaUseCase;
import com.sealmail.app.usecase.certificate.CreateRootCaUseCase;
import com.sealmail.app.usecase.certificate.IssueEndEntityUseCase;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class CertificateInitializer {

    private final CertificateStoreStatusUseCase certificateStoreStatusUseCase;
    private final CreateRootCaUseCase createRootCaUseCase;
    private final CreateIntermediateCaUseCase createIntermediateCaUseCase;
    private final IssueEndEntityUseCase issueEndEntityUseCase;
    private final boolean initCertsEnabled;

    public CertificateInitializer(CertificateStoreStatusUseCase certificateStoreStatusUseCase,
                                  CreateRootCaUseCase createRootCaUseCase,
                                  CreateIntermediateCaUseCase createIntermediateCaUseCase,
                                  IssueEndEntityUseCase issueEndEntityUseCase,
                                  @Value("${sealmail.init.certs.enabled:true}") boolean initCertsEnabled) {
        this.certificateStoreStatusUseCase = certificateStoreStatusUseCase;
        this.createRootCaUseCase = createRootCaUseCase;
        this.createIntermediateCaUseCase = createIntermediateCaUseCase;
        this.issueEndEntityUseCase = issueEndEntityUseCase;
        this.initCertsEnabled = initCertsEnabled;
    }

    @PostConstruct
    public void init() {
        if (!initCertsEnabled) {
            return;
        }

        if (!certificateStoreStatusUseCase.isEmpty()) {
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
            log.info("  seeded Root CA ({}): {}", algo, root.getId().substring(0, 16) + "...");

            CertificateResponse intermediate = createIntermediateCaUseCase.execute(
                    CreateIntermediateCaRequest.builder()
                            .rootCaId(root.getId())
                            .algorithm(algo)
                            .commonName("SealMail " + algo + " Intermediate CA")
                            .alias(algo + " Intermediate CA")
                            .build(),
                    system);
            log.info("  seeded Intermediate CA ({}): {}", algo, intermediate.getId().substring(0, 16) + "...");

            for (String owner : List.of("sender@example.test", "recipient@example.test")) {
                CertificateResponse ee = issueEndEntityUseCase.execute(
                        IssueEndEntityRequest.builder()
                                .intermediateCaId(intermediate.getId())
                                .ownerEmail(owner)
                                .algorithm(algo)
                                .alias(owner.split("@")[0] + "-" + algo)
                                .trusted(Boolean.TRUE)
                                .build(),
                        system);
                log.info("    end-entity {} ({}): {}", owner, algo, ee.getId().substring(0, 16) + "...");
            }
        }
        log.info("Seed complete: 2 Roots + 2 Intermediates + 4 end-entities.");
    }
}
