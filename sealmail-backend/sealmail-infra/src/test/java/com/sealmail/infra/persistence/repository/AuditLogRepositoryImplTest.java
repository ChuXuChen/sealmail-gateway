package com.sealmail.infra.persistence.repository;

import com.sealmail.domain.audit.AuditLog;
import com.sealmail.domain.audit.AuditLogRepository;
import com.sealmail.domain.audit.AuditLogType;
import com.sealmail.infra.persistence.mapper.AuditLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = AuditLogRepositoryImplTest.TestConfig.class)
@ContextConfiguration(initializers = AuditLogRepositoryImplTest.PostgresSchemaInitializer.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/sealmail?currentSchema=sealmail_test",
        "spring.datasource.username=sealmail",
        "spring.datasource.password=${SEALMAIL_TEST_DB_PASSWORD:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=sealmail_test",
        "spring.flyway.default-schema=sealmail_test",
        "spring.flyway.clean-disabled=false",
        "spring.flyway.baseline-on-migrate=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.default_schema=sealmail_test",
        "spring.jpa.show-sql=false",
        "spring.sql.init.mode=never"
})
@EnabledIfEnvironmentVariable(named = "SEALMAIL_TEST_DB_PASSWORD", matches = ".+")
class AuditLogRepositoryImplTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FailingAuditWriter failingAuditWriter;

    @Autowired
    private AuditLogRepository repository;

    @org.junit.jupiter.api.BeforeEach
    void cleanAuditLog() {
        jdbcTemplate.update("DELETE FROM sealmail_test.audit_log");
    }

    @Test
    void saveCommitsAuditLogWhenOuterTransactionRollsBack() {
        String id = UUID.randomUUID().toString();

        assertThrows(RuntimeException.class, () -> failingAuditWriter.writeAndRollback(id));

        assertEquals(AuditLogType.USER_LOGIN_FAILED, repository.findById(id).orElseThrow().getType());
    }

    @Test
    void searchFiltersByTypesAndSuccess() {
        repository.save(auditLog(AuditLogType.USER_LOGIN, true));
        repository.save(auditLog(AuditLogType.USER_LOGIN_FAILED, false));
        repository.save(auditLog(AuditLogType.CERTIFICATE_IMPORTED, true));

        var results = repository.search(
                java.util.List.of(AuditLogType.USER_LOGIN, AuditLogType.USER_LOGIN_FAILED),
                false,
                1,
                20
        );

        assertEquals(1, results.size());
        assertEquals(AuditLogType.USER_LOGIN_FAILED, results.getFirst().getType());
        assertEquals(1, repository.countSearch(
                java.util.List.of(AuditLogType.USER_LOGIN, AuditLogType.USER_LOGIN_FAILED),
                false
        ));
    }

    private AuditLog auditLog(AuditLogType type, boolean success) {
        return AuditLog.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .username("admin")
                .ipAddress("127.0.0.1")
                .action(type.name())
                .success(success)
                .errorMessage(success ? null : "failed")
                .build();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.sealmail.infra.persistence.entity")
    @EnableTransactionManagement
    @Import({AuditLogRepositoryImpl.class, AuditLogMapper.class, FailingAuditWriter.class})
    static class TestConfig {
    }

    static class PostgresSchemaInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            try (var connection = DriverManager.getConnection(
                    "jdbc:postgresql://localhost:5432/sealmail",
                    "sealmail",
                    System.getenv().getOrDefault("SEALMAIL_TEST_DB_PASSWORD", ""));
                 var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS sealmail_test CASCADE");
                statement.execute("CREATE SCHEMA sealmail_test");
            } catch (Exception e) {
                throw new IllegalStateException("Failed to reset PostgreSQL test schema", e);
            }
        }
    }

    @Service
    static class FailingAuditWriter {
        private final AuditLogRepository repository;

        FailingAuditWriter(AuditLogRepository repository) {
            this.repository = repository;
        }

        @Transactional
        void writeAndRollback(String id) {
            AuditLog log = AuditLog.builder()
                    .id(id)
                    .type(AuditLogType.USER_LOGIN_FAILED)
                    .username("missing-user")
                    .ipAddress("127.0.0.1")
                    .action(AuditLogType.USER_LOGIN_FAILED.name())
                    .success(false)
                    .errorMessage("用户名或密码错误")
                    .build();
            repository.save(log);
            throw new RuntimeException("rollback outer transaction");
        }
    }
}
