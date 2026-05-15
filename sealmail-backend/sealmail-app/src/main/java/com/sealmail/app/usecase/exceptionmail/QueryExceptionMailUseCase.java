package com.sealmail.app.usecase.exceptionmail;

import com.sealmail.app.dto.common.PageRequest;
import com.sealmail.app.dto.common.PageResponse;
import com.sealmail.app.dto.response.ExceptionMailItemResponse;
import com.sealmail.app.dto.response.ExceptionMailStatsResponse;
import com.sealmail.app.exception.ResourceNotFoundException;
import com.sealmail.app.mapper.ExceptionMailDtoMapper;
import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.exceptionmail.ExceptionMail;
import com.sealmail.domain.exceptionmail.ExceptionMailRepository;
import com.sealmail.domain.quarantine.QuarantineReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QueryExceptionMailUseCase {

    private final ExceptionMailRepository exceptionMailRepository;
    private final ExceptionMailDtoMapper mapper;
    private final PermissionChecker permissionChecker;

    public PageResponse<ExceptionMailItemResponse> findAll(
            PageRequest pageRequest,
            QuarantineReason reason,
            UserContext user) {

        permissionChecker.checkCanViewQuarantine(user);

        int offset = (pageRequest.getPage() - 1) * pageRequest.getSize();
        List<ExceptionMail> mails = reason != null
                ? exceptionMailRepository.findByReason(reason, offset, pageRequest.getSize())
                : exceptionMailRepository.findAll(offset, pageRequest.getSize());
        long total = reason != null
                ? exceptionMailRepository.countByReason(reason)
                : exceptionMailRepository.count();

        return PageResponse.of(
                mails.stream().map(mapper::toResponse).toList(),
                total,
                pageRequest);
    }

    public ExceptionMailItemResponse findById(String id, UserContext user) {
        permissionChecker.checkCanViewQuarantine(user);

        ExceptionMail mail = exceptionMailRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExceptionMail", id));
        return mapper.toResponse(mail);
    }

    public ExceptionMailStatsResponse getStats(UserContext user) {
        permissionChecker.checkCanViewQuarantine(user);

        Map<String, Long> byReason = new HashMap<>();
        for (QuarantineReason reason : QuarantineReason.values()) {
            byReason.put(reason.name(), exceptionMailRepository.countByReason(reason));
        }

        return ExceptionMailStatsResponse.builder()
                .total(exceptionMailRepository.count())
                .byReason(byReason)
                .build();
    }
}
