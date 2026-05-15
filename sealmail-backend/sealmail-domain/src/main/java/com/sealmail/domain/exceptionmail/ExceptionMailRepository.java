package com.sealmail.domain.exceptionmail;

import com.sealmail.domain.quarantine.QuarantineReason;

import java.util.List;
import java.util.Optional;

public interface ExceptionMailRepository {

    ExceptionMail save(ExceptionMail exceptionMail);

    Optional<ExceptionMail> findById(String id);

    List<ExceptionMail> findByMessageId(String messageId);

    List<ExceptionMail> findAll(int offset, int limit);

    List<ExceptionMail> findByReason(QuarantineReason reason, int offset, int limit);

    long count();

    long countByReason(QuarantineReason reason);
}
