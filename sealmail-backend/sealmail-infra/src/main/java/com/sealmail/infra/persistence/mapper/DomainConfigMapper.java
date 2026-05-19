package com.sealmail.infra.persistence.mapper;

import com.sealmail.domain.policy.DomainConfig;
import com.sealmail.domain.policy.DeliveryTransportProfile;
import com.sealmail.domain.policy.DecryptionMode;
import com.sealmail.domain.policy.EncryptionPolicy;
import com.sealmail.domain.policy.PreferredAlgorithm;
import com.sealmail.infra.persistence.entity.DomainConfigEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DomainConfigMapper {

    private static final Logger log = LoggerFactory.getLogger(DomainConfigMapper.class);

    public DomainConfigEntity toEntity(DomainConfig domainConfig) {
        DomainConfigEntity entity = new DomainConfigEntity();
        entity.setId(domainConfig.getId());
        entity.setDomainName(domainConfig.getDomain());
        entity.setLocalDomain(domainConfig.isLocalDomain());
        entity.setEncryptionPolicy(domainConfig.getEncryptionPolicy().name());
        entity.setPreferredAlgorithm(domainConfig.getPreferredAlgorithm().name());
        entity.setSigningEnabled(domainConfig.isSigningEnabled());
        entity.setDkimEnabled(domainConfig.isDkimEnabled());
        entity.setDeliveryHost(domainConfig.getDeliveryHost());
        entity.setDeliveryPort(domainConfig.getDeliveryPort());
        entity.setDeliveryTransportProfile(domainConfig.getDeliveryTransportProfile().name());
        entity.setDecryptionMode(domainConfig.getDecryptionMode().name());
        entity.setActive(domainConfig.isActive());
        return entity;
    }

    public DomainConfig toDomain(DomainConfigEntity entity) {
        DomainConfig config = DomainConfig.create(
                entity.getId(),
                entity.getDomainName(),
                entity.isLocalDomain()
        );

        // Restore policy state
        if (entity.getEncryptionPolicy() != null
                && !entity.getEncryptionPolicy().equals(config.getEncryptionPolicy().name())) {
            config.changePolicy(EncryptionPolicy.valueOf(entity.getEncryptionPolicy()));
        }
        if (entity.getPreferredAlgorithm() != null
                && !entity.getPreferredAlgorithm().equals(config.getPreferredAlgorithm().name())) {
            config.changePreferredAlgorithm(PreferredAlgorithm.valueOf(entity.getPreferredAlgorithm()));
        }
        if (entity.isSigningEnabled()) {
            config.enableSigning();
        }
        if (!entity.isActive()) {
            try {
                config.deactivate();
            } catch (RuntimeException e) {
                log.debug("Skipped invalid persisted domain activation state for {}: {}",
                        entity.getId(), e.getMessage());
            }
        }
        config.setDkimEnabled(entity.isDkimEnabled());
        DeliveryTransportProfile profile = entity.getDeliveryTransportProfile() != null
                ? DeliveryTransportProfile.valueOf(entity.getDeliveryTransportProfile())
                : DeliveryTransportProfile.fromLegacyPort(entity.getDeliveryPort());
        config.configureDeliveryRoute(entity.getDeliveryHost(), profile, entity.getDeliveryPort());
        if (entity.getDecryptionMode() != null) {
            config.changeDecryptionMode(DecryptionMode.valueOf(entity.getDecryptionMode()));
        }

        config.clearDomainEvents();
        return config;
    }
}
