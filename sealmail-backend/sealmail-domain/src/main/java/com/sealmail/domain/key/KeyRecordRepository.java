package com.sealmail.domain.key;

import com.sealmail.domain.shared.model.EmailAddress;

import java.util.List;
import java.util.Optional;

public interface KeyRecordRepository {

    KeyRecord save(KeyRecord keyRecord);

    Optional<KeyRecord> findById(String keyId);

    Optional<KeyRecord> findActiveByCertificateId(String certificateId);

    List<KeyRecord> findActiveByOwnerAndPurpose(EmailAddress owner, KeyPurpose purpose);
}
