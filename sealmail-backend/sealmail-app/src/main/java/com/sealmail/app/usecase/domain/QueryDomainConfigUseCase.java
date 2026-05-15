package com.sealmail.app.usecase.domain;

import com.sealmail.app.dto.response.DomainConfigResponse;
import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.exception.ResourceNotFoundException;
import com.sealmail.app.mapper.DomainDtoMapper;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.policy.DomainConfig;
import com.sealmail.domain.policy.DomainConfigRepository;
import com.sealmail.domain.policy.DomainName;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 查询域名配置用例
 */
@Service
@RequiredArgsConstructor
public class QueryDomainConfigUseCase {

    private final DomainConfigRepository repository;
    private final DomainDtoMapper mapper;

    @Transactional(readOnly = true)
    public List<DomainConfigResponse> findAll(UserContext currentUser) {
        if (!currentUser.isAdmin()) {
            throw BusinessException.forbidden("只有管理员可以查看域名配置");
        }
        return repository.findAll().stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DomainConfigResponse> findAllActive(UserContext currentUser) {
        if (!currentUser.isAdmin()) {
            throw BusinessException.forbidden("只有管理员可以查看域名配置");
        }
        return repository.findAllActive().stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DomainConfigResponse findById(String id, UserContext currentUser) {
        if (!currentUser.isAdmin()) {
            throw BusinessException.forbidden("只有管理员可以查看域名配置");
        }
        DomainConfig config = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.notFound("DomainConfig", id));
        return mapper.toResponse(config);
    }

    @Transactional(readOnly = true)
    public DomainConfigResponse findByDomain(String domain, UserContext currentUser) {
        if (!currentUser.isAdmin()) {
            throw BusinessException.forbidden("只有管理员可以查看域名配置");
        }
        String normalizedDomain = DomainName.requireValid(domain);
        DomainConfig config = repository.findByDomain(normalizedDomain)
                .orElseThrow(() -> ResourceNotFoundException.notFound("DomainConfig", normalizedDomain));
        return mapper.toResponse(config);
    }

    @Transactional(readOnly = true)
    public List<DomainConfigResponse> findLocalDomains(UserContext currentUser) {
        if (!currentUser.isAdmin()) {
            throw BusinessException.forbidden("只有管理员可以查看域名配置");
        }
        return repository.findLocalDomains().stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DomainConfigResponse> findRemoteDomains(UserContext currentUser) {
        if (!currentUser.isAdmin()) {
            throw BusinessException.forbidden("只有管理员可以查看域名配置");
        }
        return repository.findRemoteDomains().stream()
                .map(mapper::toResponse)
                .toList();
    }
}
