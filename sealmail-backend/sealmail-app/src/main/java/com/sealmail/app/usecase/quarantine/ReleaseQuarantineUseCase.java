package com.sealmail.app.usecase.quarantine;

import com.sealmail.app.dto.request.ReleaseQuarantineRequest;
import com.sealmail.app.dto.response.QuarantineItemResponse;
import com.sealmail.app.exception.QuarantineStateException;
import com.sealmail.app.exception.ResourceNotFoundException;
import com.sealmail.app.mapper.QuarantineDtoMapper;
import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.config.QuarantinePolicyPort;
import com.sealmail.domain.quarantine.QuarantineRepository;
import com.sealmail.domain.quarantine.QuarantineStatus;
import com.sealmail.domain.quarantine.QuarantinedMail;
import com.sealmail.domain.quarantine.spi.QuarantineMailReleaseRelay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

@Service
@Transactional(readOnly = true)
public class ReleaseQuarantineUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReleaseQuarantineUseCase.class);

    private final QuarantineRepository quarantineRepository;
    private final QuarantineDtoMapper mapper;
    private final PermissionChecker permissionChecker;
    private final QuarantineMailReleaseRelay releaseRelay;
    private final QuarantinePolicyPort quarantinePolicyPort;
    private final TransactionOperations releaseStateTransaction;

    public ReleaseQuarantineUseCase(QuarantineRepository quarantineRepository,
                                    QuarantineDtoMapper mapper,
                                    PermissionChecker permissionChecker,
                                    QuarantineMailReleaseRelay releaseRelay,
                                    QuarantinePolicyPort quarantinePolicyPort,
                                    PlatformTransactionManager transactionManager) {
        this.quarantineRepository = quarantineRepository;
        this.mapper = mapper;
        this.permissionChecker = permissionChecker;
        this.releaseRelay = releaseRelay;
        this.quarantinePolicyPort = quarantinePolicyPort;
        this.releaseStateTransaction = releaseStateTransaction(transactionManager);
    }

    private static TransactionOperations releaseStateTransaction(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public QuarantineItemResponse execute(
            String id,
            ReleaseQuarantineRequest request,
            UserContext user) {

        permissionChecker.checkCanManageQuarantine(user);
        ReleaseQuarantineRequest effectiveRequest = request != null
                ? request
                : new ReleaseQuarantineRequest();

        PendingRelease pendingRelease = releaseStateTransaction.execute(status ->
                beginRelease(id, effectiveRequest, user));

        try {
            releaseRelay.relay(pendingRelease.mail(), pendingRelease.encryptBeforeRelease());
        } catch (RuntimeException e) {
            restoreAfterRelayFailure(pendingRelease, e);
            throw e;
        }

        QuarantinedMail released = releaseStateTransaction.execute(status ->
                completeRelease(pendingRelease));

        log.info("User [{}] released quarantined mail: {}", user.getUserId(), id);

        return mapper.toResponse(released);
    }

    private PendingRelease beginRelease(String id, ReleaseQuarantineRequest effectiveRequest, UserContext user) {
        QuarantinedMail mail = quarantineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("QuarantinedMail", id));

        if (mail.getStatus() == QuarantineStatus.RELEASED) {
            throw QuarantineStateException.alreadyReleased(id);
        }
        if (mail.getStatus() == QuarantineStatus.RELEASING) {
            throw QuarantineStateException.invalidStateForOperation(id, "release");
        }
        if (mail.getStatus() == QuarantineStatus.REJECTED) {
            if (!Boolean.TRUE.equals(effectiveRequest.getForce())) {
                throw QuarantineStateException.invalidStateForOperation(id, "release");
            }
        }
        if (!mail.hasRawContent()) {
            throw new MissingQuarantineMailContentException(id);
        }

        String releasedBy = effectiveRequest.getReleasedBy() != null
                ? effectiveRequest.getReleasedBy()
                : user.getUserId();
        if (releasedBy == null || releasedBy.isBlank()) {
            releasedBy = user.getUsername();
        }

        boolean encryptBeforeRelease = Boolean.TRUE.equals(effectiveRequest.getEncryptBeforeRelease())
                || quarantinePolicyPort.getSettings().releaseRequiresEncryption();
        String releaseComment = releaseComment(effectiveRequest.getComment(), encryptBeforeRelease);
        QuarantineStatus previousStatus = mail.getStatus();
        Instant previousResolvedAt = mail.getResolvedAt();
        String previousProcessedBy = mail.getProcessedBy();
        String previousProcessComment = mail.getProcessComment();

        mail.startRelease(releasedBy, releaseComment, Boolean.TRUE.equals(effectiveRequest.getForce()));
        quarantineRepository.save(mail);

        return new PendingRelease(mail, encryptBeforeRelease, releasedBy, releaseComment,
                previousStatus, previousResolvedAt, previousProcessedBy, previousProcessComment);
    }

    private QuarantinedMail completeRelease(PendingRelease pendingRelease) {
        QuarantinedMail mail = quarantineRepository.findById(pendingRelease.mail().getId())
                .orElseThrow(() -> new ResourceNotFoundException("QuarantinedMail", pendingRelease.mail().getId()));
        if (mail.getStatus() == QuarantineStatus.RELEASED) {
            return mail;
        }
        if (mail.getStatus() != QuarantineStatus.RELEASING) {
            throw QuarantineStateException.invalidStateForOperation(mail.getId(), "complete release");
        }
        mail.release(pendingRelease.releasedBy(), pendingRelease.releaseComment());
        quarantineRepository.save(mail);
        return mail;
    }

    private void restoreAfterRelayFailure(PendingRelease pendingRelease, RuntimeException relayFailure) {
        try {
            releaseStateTransaction.executeWithoutResult(status -> {
                quarantineRepository.findById(pendingRelease.mail().getId())
                        .filter(mail -> mail.getStatus() == QuarantineStatus.RELEASING)
                        .ifPresent(mail -> {
                            mail.restoreAfterFailedRelease(
                                    pendingRelease.previousStatus(),
                                    pendingRelease.previousResolvedAt(),
                                    pendingRelease.previousProcessedBy(),
                                    pendingRelease.previousProcessComment());
                            quarantineRepository.save(mail);
                        });
            });
        } catch (RuntimeException restoreFailure) {
            relayFailure.addSuppressed(restoreFailure);
            log.warn("Failed to restore quarantine release state for mail {} after relay failure",
                    pendingRelease.mail().getId(), restoreFailure);
        }
    }

    private String releaseComment(String comment, boolean encryptBeforeRelease) {
        if (!encryptBeforeRelease) {
            return comment;
        }
        if (comment == null || comment.isBlank()) {
            return "Encrypted before release";
        }
        return comment + " | Encrypted before release";
    }

    private record PendingRelease(
            QuarantinedMail mail,
            boolean encryptBeforeRelease,
            String releasedBy,
            String releaseComment,
            QuarantineStatus previousStatus,
            Instant previousResolvedAt,
            String previousProcessedBy,
            String previousProcessComment) {
    }
}
