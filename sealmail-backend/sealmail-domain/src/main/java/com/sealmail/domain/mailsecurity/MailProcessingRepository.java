package com.sealmail.domain.mailsecurity;

import java.util.List;
import java.util.Optional;

public interface MailProcessingRepository {

    MailProcessing save(MailProcessing mailProcessing);

    Optional<MailProcessing> findById(String id);

    List<MailProcessing> findByMessageId(String messageId);

    List<MailProcessing> findByResult(ProcessingResult result);

    List<MailProcessing> findRecent(int page, int size);

    long count();
}
