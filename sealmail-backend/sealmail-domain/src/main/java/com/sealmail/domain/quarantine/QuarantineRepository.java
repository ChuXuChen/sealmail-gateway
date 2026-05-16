package com.sealmail.domain.quarantine;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface QuarantineRepository {

    QuarantinedMail save(QuarantinedMail quarantinedMail);

    Optional<QuarantinedMail> findById(String id);

    List<QuarantinedMail> findByStatus(QuarantineStatus status);

    List<QuarantinedMail> findByMessageId(String messageId);

    List<QuarantinedMail> findAll(int offset, int limit);

    List<QuarantinedMail> findByStatus(QuarantineStatus status, int offset, int limit);

    List<QuarantinedMail> findByReason(QuarantineReason reason, int offset, int limit);

    List<QuarantinedMail> findByStatusAndReason(QuarantineStatus status, QuarantineReason reason, int offset, int limit);

    void deleteById(String id);

    int deleteCreatedBefore(Instant cutoff);

    long count();

    long countByStatus(QuarantineStatus status);

    long countByReason(QuarantineReason reason);

    long countByStatusAndReason(QuarantineStatus status, QuarantineReason reason);
}
