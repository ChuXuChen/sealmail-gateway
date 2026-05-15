package com.sealmail.app.usecase.quarantine;

import com.sealmail.app.dto.request.ReleaseQuarantineRequest;
import com.sealmail.app.dto.response.QuarantineItemResponse;
import com.sealmail.app.exception.QuarantineStateException;
import com.sealmail.app.exception.ResourceNotFoundException;
import com.sealmail.app.mapper.QuarantineDtoMapper;
import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.quarantine.QuarantineRepository;
import com.sealmail.domain.quarantine.QuarantineStatus;
import com.sealmail.domain.quarantine.QuarantinedMail;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReleaseQuarantineUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReleaseQuarantineUseCase.class);

    private final QuarantineRepository quarantineRepository;
    private final QuarantineDtoMapper mapper;
    private final PermissionChecker permissionChecker;
    private final QuarantineMailReleaseRelay releaseRelay;

    @Transactional
    public QuarantineItemResponse execute(
            String id,
            ReleaseQuarantineRequest request,
            UserContext user) {

        permissionChecker.checkCanManageQuarantine(user);
        ReleaseQuarantineRequest effectiveRequest = request != null
                ? request
                : new ReleaseQuarantineRequest();

        QuarantinedMail mail = quarantineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("QuarantinedMail", id));

        // Validate state
        if (mail.getStatus() == QuarantineStatus.RELEASED) {
            throw QuarantineStateException.alreadyReleased(id);
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

        boolean encryptBeforeRelease = Boolean.TRUE.equals(effectiveRequest.getEncryptBeforeRelease());
        releaseRelay.relay(mail, encryptBeforeRelease);

        mail.release(releasedBy, releaseComment(effectiveRequest.getComment(), encryptBeforeRelease),
                Boolean.TRUE.equals(effectiveRequest.getForce()));
        quarantineRepository.save(mail);

        log.info("User [{}] released quarantined mail: {}", user.getUserId(), id);

        return mapper.toResponse(mail);
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
}
