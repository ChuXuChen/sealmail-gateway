package com.sealmail.app.usecase.quarantine;

import com.sealmail.app.dto.request.RejectQuarantineRequest;
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

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RejectQuarantineUseCase {

    private static final Logger log = LoggerFactory.getLogger(RejectQuarantineUseCase.class);

    private final QuarantineRepository quarantineRepository;
    private final QuarantineDtoMapper mapper;
    private final PermissionChecker permissionChecker;

    @Transactional
    public void batchReject(
            List<String> ids,
            RejectQuarantineRequest request,
            UserContext user) {

        permissionChecker.checkCanManageQuarantine(user);
        RejectQuarantineRequest effectiveRequest = request != null
                ? request
                : new RejectQuarantineRequest();
        List<QuarantinedMail> mails = ids.stream()
                .map(this::findRejectable)
                .toList();

        for (QuarantinedMail mail : mails) {
            reject(mail, effectiveRequest, user);
        }
    }

    @Transactional
    public QuarantineItemResponse execute(
            String id,
            RejectQuarantineRequest request,
            UserContext user) {

        permissionChecker.checkCanManageQuarantine(user);
        RejectQuarantineRequest effectiveRequest = request != null
                ? request
                : new RejectQuarantineRequest();
        QuarantinedMail mail = findRejectable(id);

        reject(mail, effectiveRequest, user);

        log.info("User [{}] rejected quarantined mail: {}", user.getUserId(), id);

        return mapper.toResponse(mail);
    }

    private QuarantinedMail findRejectable(String id) {
        QuarantinedMail mail = quarantineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("QuarantinedMail", id));

        if (mail.getStatus() == QuarantineStatus.REJECTED) {
            throw QuarantineStateException.alreadyRejected(id);
        }
        if (mail.getStatus() != QuarantineStatus.QUARANTINED) {
            throw QuarantineStateException.invalidStateForOperation(id, "reject");
        }
        return mail;
    }

    private void reject(QuarantinedMail mail, RejectQuarantineRequest request, UserContext user) {
        String rejectedBy = request.getRejectedBy() != null
                ? request.getRejectedBy()
                : user.getUserId();
        if (rejectedBy == null || rejectedBy.isBlank()) {
            rejectedBy = user.getUsername();
        }

        mail.reject(rejectedBy, request.getComment());
        quarantineRepository.save(mail);
    }
}
