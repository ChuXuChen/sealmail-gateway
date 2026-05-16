package com.sealmail.app.usecase.quarantine;

import com.sealmail.app.dto.request.RepairQuarantineReleaseRequest;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RepairQuarantineReleaseUseCase {

    private final QuarantineRepository quarantineRepository;
    private final QuarantineDtoMapper mapper;
    private final PermissionChecker permissionChecker;

    @Transactional
    public QuarantineItemResponse complete(String id, RepairQuarantineReleaseRequest request, UserContext user) {
        permissionChecker.checkCanManageQuarantine(user);
        QuarantinedMail mail = releasingMail(id);
        String operator = operator(request, user);
        mail.release(operator, comment(request, "Operator confirmed quarantined mail was already relayed"));
        quarantineRepository.save(mail);
        return mapper.toResponse(mail);
    }

    @Transactional
    public QuarantineItemResponse restore(String id, RepairQuarantineReleaseRequest request, UserContext user) {
        permissionChecker.checkCanManageQuarantine(user);
        QuarantinedMail mail = releasingMail(id);
        mail.restoreReleaseForRetry(
                operator(request, user),
                comment(request, "Operator confirmed quarantined mail was not relayed"));
        quarantineRepository.save(mail);
        return mapper.toResponse(mail);
    }

    private QuarantinedMail releasingMail(String id) {
        QuarantinedMail mail = quarantineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("QuarantinedMail", id));
        if (mail.getStatus() != QuarantineStatus.RELEASING) {
            throw QuarantineStateException.invalidStateForOperation(id, "repair release");
        }
        return mail;
    }

    private String operator(RepairQuarantineReleaseRequest request, UserContext user) {
        String operator = request != null ? request.getOperator() : null;
        if (operator == null || operator.isBlank()) {
            operator = user.getUserId();
        }
        if (operator == null || operator.isBlank()) {
            operator = user.getUsername();
        }
        return operator;
    }

    private String comment(RepairQuarantineReleaseRequest request, String defaultComment) {
        if (request == null || request.getComment() == null || request.getComment().isBlank()) {
            return defaultComment;
        }
        return request.getComment();
    }
}
