package com.sealmail.app.usecase.domain;

import com.sealmail.app.dto.request.UpdateDomainConfigRequest;
import com.sealmail.app.dto.response.DomainConfigResponse;
import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.exception.ResourceNotFoundException;
import com.sealmail.app.mapper.DomainDtoMapper;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.policy.DomainConfig;
import com.sealmail.domain.policy.DomainConfigRepository;
import com.sealmail.domain.policy.DeliveryTransportProfile;
import com.sealmail.domain.policy.DecryptionMode;
import com.sealmail.domain.policy.EncryptionPolicy;
import com.sealmail.domain.policy.PreferredAlgorithm;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 更新域名配置用例
 */
@Service
@RequiredArgsConstructor
public class UpdateDomainConfigUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateDomainConfigUseCase.class);

    private final DomainConfigRepository repository;
    private final DomainDtoMapper mapper;

    @Transactional
    public DomainConfigResponse execute(String id, UpdateDomainConfigRequest request, UserContext currentUser) {
        if (!currentUser.isAdmin()) {
            throw BusinessException.forbidden("只有管理员可以更新域名配置");
        }

        DomainConfig config = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.notFound("DomainConfig", id));

        if (request.getEncryptionPolicy() != null) {
            config.changePolicy(parseEnum(
                    EncryptionPolicy.class,
                    request.getEncryptionPolicy(),
                    "不支持的加密策略: "
            ));
        }

        if (request.getPreferredAlgorithm() != null) {
            config.changePreferredAlgorithm(parseEnum(
                    PreferredAlgorithm.class,
                    request.getPreferredAlgorithm(),
                    "不支持的算法偏好: "
            ));
        }

        if (request.getSigningEnabled() != null) {
            if (request.getSigningEnabled()) {
                config.enableSigning();
            } else {
                config.disableSigning();
            }
        }

        if (request.getDkimEnabled() != null) {
            config.setDkimEnabled(request.getDkimEnabled());
        }

        if (request.getDeliveryHost() != null
                || request.getDeliveryTransportProfile() != null
                || request.getDeliveryPort() != null) {
            configureDeliveryRoute(
                    config,
                    request.getDeliveryHost() != null ? request.getDeliveryHost() : config.getDeliveryHost(),
                    request.getDeliveryTransportProfile(),
                    request.getDeliveryPort());
        }

        if (request.getDecryptionMode() != null) {
            config.changeDecryptionMode(parseEnum(
                    DecryptionMode.class,
                    request.getDecryptionMode(),
                    "不支持的解密模式: "
            ));
        }

        if (request.getActive() != null) {
            if (request.getActive()) {
                config.activate();
            } else {
                config.deactivate();
            }
        }

        repository.save(config);

        log.info("Domain config updated: {} by {}", config.getDomain(), currentUser.getEmail());

        return mapper.toResponse(config);
    }

    private void configureDeliveryRoute(DomainConfig config,
                                        String deliveryHost,
                                        String deliveryTransportProfile,
                                        Integer legacyDeliveryPort) {
        try {
            DeliveryTransportProfile profile = deliveryTransportProfile != null
                    ? parseEnum(DeliveryTransportProfile.class, deliveryTransportProfile, "不支持的投递传输配置: ")
                    : (legacyDeliveryPort != null
                    ? DeliveryTransportProfile.fromLegacyPort(legacyDeliveryPort)
                    : config.getDeliveryTransportProfile());
            config.configureDeliveryRoute(deliveryHost, profile);
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest(e.getMessage());
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, String messagePrefix) {
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest(messagePrefix + value);
        }
    }
}
