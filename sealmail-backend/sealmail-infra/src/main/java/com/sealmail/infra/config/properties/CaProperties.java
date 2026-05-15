package com.sealmail.infra.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Internal-CA configuration: base URL prefix for the CRL Distribution Point
 * extension embedded in issued end-entity certificates.
 */
@Data
@Component
@ConfigurationProperties(prefix = "sealmail.ca")
public class CaProperties {

    /**
     * Prefix for CRL DP URLs in issued certs. Full URL = {@code crlBaseUrl + caCertId}.
     * The CRL endpoint is served anonymously at {@code /api/v1/crl/{caCertId}}.
     */
    private String crlBaseUrl = "http://localhost:8080/api/v1/crl/";

    /** Default validity for root CAs (days). */
    private int defaultRootValidityDays = 3650;

    /** Default validity for intermediate CAs (days). */
    private int defaultIntermediateValidityDays = 1825;

    /** Default validity for end-entity certs (days). */
    private int defaultEndEntityValidityDays = 365;
}
