package com.sealmail.infra.persistence.mapper;

import com.sealmail.domain.key.KeyPurpose;
import com.sealmail.domain.key.KeyRecord;
import com.sealmail.domain.key.KeyStatus;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.persistence.entity.ManagedKeyEntity;
import org.springframework.stereotype.Component;

@Component
public class ManagedKeyMapper {

    public ManagedKeyEntity toEntity(KeyRecord keyRecord) {
        ManagedKeyEntity entity = new ManagedKeyEntity();
        entity.setKeyId(keyRecord.getKeyId());
        entity.setOwnerEmail(keyRecord.getOwner().getValue());
        entity.setAlgorithm(keyRecord.getAlgorithm());
        entity.setPurpose(keyRecord.getPurpose().name());
        entity.setProvider(keyRecord.getProvider());
        entity.setProviderRef(keyRecord.getProviderRef());
        entity.setCertificateId(keyRecord.getCertificateId());
        entity.setStatus(keyRecord.getStatus().name());
        entity.setLastUsedAt(keyRecord.getLastUsedAt());
        entity.setRotatedFromKeyId(keyRecord.getRotatedFromKeyId());
        entity.setCreatedAt(keyRecord.getCreatedAt());
        entity.setUpdatedAt(keyRecord.getUpdatedAt());
        return entity;
    }

    public KeyRecord toDomain(ManagedKeyEntity entity) {
        return new KeyRecord(
                entity.getKeyId(),
                new EmailAddress(entity.getOwnerEmail()),
                entity.getAlgorithm(),
                KeyPurpose.valueOf(entity.getPurpose()),
                entity.getProvider(),
                entity.getProviderRef(),
                entity.getCertificateId(),
                KeyStatus.valueOf(entity.getStatus()),
                entity.getLastUsedAt(),
                entity.getRotatedFromKeyId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
