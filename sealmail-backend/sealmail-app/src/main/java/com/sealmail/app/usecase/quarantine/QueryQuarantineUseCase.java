package com.sealmail.app.usecase.quarantine;

import com.sealmail.app.dto.common.PageRequest;
import com.sealmail.app.dto.common.PageResponse;
import com.sealmail.app.dto.response.QuarantineItemResponse;
import com.sealmail.app.dto.response.QuarantineStatsResponse;
import com.sealmail.app.exception.ResourceNotFoundException;
import com.sealmail.app.mapper.QuarantineDtoMapper;
import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.quarantine.QuarantineRepository;
import com.sealmail.domain.quarantine.QuarantineStatus;
import com.sealmail.domain.quarantine.QuarantinedMail;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QueryQuarantineUseCase {

    private static final Logger log = LoggerFactory.getLogger(QueryQuarantineUseCase.class);

    private final QuarantineRepository quarantineRepository;
    private final QuarantineDtoMapper mapper;
    private final PermissionChecker permissionChecker;

    public PageResponse<QuarantineItemResponse> findAll(
            PageRequest pageRequest,
            QuarantineReason reason,
            UserContext user) {

        permissionChecker.checkCanViewQuarantine(user);

        int offset = (pageRequest.getPage() - 1) * pageRequest.getSize();
        List<QuarantinedMail> mails = findItems(reason, offset, pageRequest.getSize());

        long total = countItems(reason);

        log.debug("Found {} DLP quarantined mails (total: {})", mails.size(), total);

        return PageResponse.of(
                mails.stream().map(mapper::toResponse).toList(),
                total,
                pageRequest);
    }

    public PageResponse<QuarantineItemResponse> findAll(
            PageRequest pageRequest,
            UserContext user) {
        return findAll(pageRequest, (QuarantineReason) null, user);
    }

    public PageResponse<QuarantineItemResponse> findAll(
            PageRequest pageRequest,
            String reason,
            UserContext user) {
        return findAll(pageRequest, parseReason(reason), user);
    }

    private List<QuarantinedMail> findItems(QuarantineReason reason, int offset, int size) {
        if (reason != null) {
            return quarantineRepository.findByReason(reason, offset, size);
        }
        return quarantineRepository.findAll(offset, size);
    }

    private long countItems(QuarantineReason reason) {
        if (reason != null) {
            return quarantineRepository.countByReason(reason);
        }
        return quarantineRepository.count();
    }

    public QuarantineItemResponse findById(String id, UserContext user) {
        permissionChecker.checkCanViewQuarantine(user);

        QuarantinedMail mail = quarantineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("QuarantinedMail", id));

        return mapper.toResponse(mail);
    }

    public QuarantineStatsResponse getStats(UserContext user) {
        permissionChecker.checkCanViewQuarantine(user);

        Map<String, Long> byReason = new HashMap<>();
        for (QuarantineReason reason : QuarantineReason.values()) {
            byReason.put(reason.name(), quarantineRepository.countByReason(reason));
        }

        return QuarantineStatsResponse.builder()
                .total(quarantineRepository.count())
                .pending(quarantineRepository.countByStatus(QuarantineStatus.QUARANTINED))
                .released(quarantineRepository.countByStatus(QuarantineStatus.RELEASED))
                .rejected(quarantineRepository.countByStatus(QuarantineStatus.REJECTED))
                .byReason(byReason)
                .build();
    }

    private QuarantineReason parseReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return QuarantineReason.valueOf(reason);
    }
}
