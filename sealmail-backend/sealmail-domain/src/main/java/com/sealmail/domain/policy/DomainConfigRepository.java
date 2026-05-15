package com.sealmail.domain.policy;

import java.util.List;
import java.util.Optional;

public interface DomainConfigRepository {

    DomainConfig save(DomainConfig domainConfig);

    Optional<DomainConfig> findById(String id);

    Optional<DomainConfig> findByDomain(String domain);

    List<DomainConfig> findAll();

    List<DomainConfig> findAllActive();

    List<DomainConfig> findLocalDomains();

    List<DomainConfig> findRemoteDomains();

    long count();

    void deleteById(String id);

    boolean existsByDomain(String domain);
}
