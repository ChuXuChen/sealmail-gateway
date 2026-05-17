package com.sealmail.app.usecase.domain;

import com.sealmail.app.dto.request.CreateDomainConfigRequest;
import com.sealmail.app.dto.response.DomainConfigResponse;
import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.mapper.DomainDtoMapper;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.policy.DomainConfig;
import com.sealmail.domain.policy.DomainConfigRepository;
import com.sealmail.domain.policy.DomainName;
import com.sealmail.domain.policy.EncryptionPolicy;
import com.sealmail.domain.policy.PreferredAlgorithm;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 创建域名配置用例
 */
@Service
@RequiredArgsConstructor
public class CreateDomainConfigUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateDomainConfigUseCase.class);

    private final DomainConfigRepository repository;
    private final DomainDtoMapper mapper;

    @Transactional
    public DomainConfigResponse execute(CreateDomainConfigRequest request, UserContext currentUser) {
        if (!currentUser.isAdmin()) {
            throw BusinessException.forbidden("只有管理员可以创建域名配置");
        }

        String domain = DomainName.requireValid(request.getDomain());

        if (repository.existsByDomain(domain)) {
            throw BusinessException.conflict("域名已存在: " + domain);
        }

        DomainConfig config = DomainConfig.create(
                UUID.randomUUID().toString(),
                domain,
                Boolean.TRUE.equals(request.getLocalDomain())
        );
        applyInitialOptions(config, request);

        repository.save(config);

        log.info("Domain config created: {} by {}", config.getDomain(), currentUser.getEmail());

        return mapper.toResponse(config);
    }

    private void applyInitialOptions(DomainConfig config, CreateDomainConfigRequest request) {
        EncryptionPolicy policy = request.getEncryptionPolicy() != null
                ? parseEnum(EncryptionPolicy.class, request.getEncryptionPolicy(), "不支持的加密策略: ")
                : defaultPolicy(request);
        config.changePolicy(policy);

        if (request.getPreferredAlgorithm() != null) {
            config.changePreferredAlgorithm(parseEnum(
                    PreferredAlgorithm.class,
                    request.getPreferredAlgorithm(),
                    "不支持的算法偏好: "
            ));
        }
        if (Boolean.TRUE.equals(request.getSigningEnabled())) {
            config.enableSigning();
        }
        if (request.getDkimEnabled() != null) {
            config.setDkimEnabled(request.getDkimEnabled());
        }
        configureDeliveryRoute(config, request.getDeliveryHost(), request.getDeliveryPort());
        if (Boolean.FALSE.equals(request.getActive())) {
            config.deactivate();
        }
    }

    private void configureDeliveryRoute(DomainConfig config, String deliveryHost, Integer deliveryPort) {
        try {
            config.configureDeliveryRoute(deliveryHost, deliveryPort);
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest(e.getMessage());
        }
    }

    private EncryptionPolicy defaultPolicy(CreateDomainConfigRequest request) {
        return Boolean.TRUE.equals(request.getLocalDomain())
                ? EncryptionPolicy.MANDATORY
                : EncryptionPolicy.ALLOW;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, String messagePrefix) {
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest(messagePrefix + value);
        }
    }
}
