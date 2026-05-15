package com.sealmail.app.usecase.domain;

import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.exception.ResourceNotFoundException;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.policy.DomainConfig;
import com.sealmail.domain.policy.DomainConfigRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 删除域名配置用例
 */
@Service
@RequiredArgsConstructor
public class DeleteDomainConfigUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeleteDomainConfigUseCase.class);

    private final DomainConfigRepository repository;

    @Transactional
    public void execute(String id, UserContext currentUser) {
        if (!currentUser.isAdmin()) {
            throw BusinessException.forbidden("只有管理员可以删除域名配置");
        }

        DomainConfig config = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.notFound("DomainConfig", id));

        repository.deleteById(id);

        log.info("Domain config deleted: {} by {}", config.getDomain(), currentUser.getEmail());
    }
}
