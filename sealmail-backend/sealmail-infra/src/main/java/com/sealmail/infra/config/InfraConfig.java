package com.sealmail.infra.config;

import com.sealmail.domain.audit.AuditContext;
import com.sealmail.domain.audit.AuditContextProvider;
import com.sealmail.domain.audit.AuditLogRepository;
import com.sealmail.domain.audit.AuditService;
import com.sealmail.domain.certificate.CertificateSelector;
import com.sealmail.domain.mailsecurity.CryptoProfileSelector;
import com.sealmail.domain.mailsecurity.MailRouter;
import com.sealmail.infra.config.properties.AuthProperties;
import com.sealmail.infra.config.properties.MailAuthProperties;
import com.sealmail.infra.config.properties.PostfixProperties;
import com.sealmail.infra.config.properties.RelayProperties;
import com.sealmail.infra.config.properties.SecurityProperties;
import com.sealmail.infra.config.properties.SmimeCryptoProperties;
import com.sealmail.infra.config.properties.SmtpServerProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "com.sealmail.infra")
@EnableConfigurationProperties({
        SmtpServerProperties.class,
        RelayProperties.class,
        PostfixProperties.class,
        SecurityProperties.class,
        MailAuthProperties.class,
        AuthProperties.class,
        SmimeCryptoProperties.class
})
public class InfraConfig {

    @Bean
    public CryptoProfileSelector cryptoProfileSelector() {
        return new CryptoProfileSelector();
    }

    @Bean
    public CertificateSelector certificateSelector(CryptoProfileSelector cryptoProfileSelector) {
        return new CertificateSelector(cryptoProfileSelector);
    }

    @Bean
    public MailRouter mailRouter() {
        return new MailRouter();
    }

    @Bean
    public AuditService auditService(AuditLogRepository auditLogRepository) {
        return new AuditService(auditLogRepository);
    }

    @Bean
    @ConditionalOnMissingBean(AuditContextProvider.class)
    public AuditContextProvider auditContextProvider() {
        return AuditContext::empty;
    }
}
