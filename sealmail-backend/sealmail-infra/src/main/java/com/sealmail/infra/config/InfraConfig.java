package com.sealmail.infra.config;

import com.sealmail.domain.certificate.CertificateSelector;
import com.sealmail.domain.audit.AuditLogRepository;
import com.sealmail.domain.audit.AuditService;
import com.sealmail.domain.mailsecurity.MailRouter;
import com.sealmail.infra.config.properties.AuthProperties;
import com.sealmail.infra.config.properties.DlpProperties;
import com.sealmail.infra.config.properties.MailAuthProperties;
import com.sealmail.infra.config.properties.PostfixProperties;
import com.sealmail.infra.config.properties.RelayProperties;
import com.sealmail.infra.config.properties.SecurityProperties;
import com.sealmail.infra.config.properties.SmtpServerProperties;
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
        DlpProperties.class
})
public class InfraConfig {

    @Bean
    public CertificateSelector certificateSelector() {
        return new CertificateSelector();
    }

    @Bean
    public MailRouter mailRouter(CertificateSelector certificateSelector) {
        return new MailRouter(certificateSelector);
    }

    @Bean
    public AuditService auditService(AuditLogRepository auditLogRepository) {
        return new AuditService(auditLogRepository);
    }
}
