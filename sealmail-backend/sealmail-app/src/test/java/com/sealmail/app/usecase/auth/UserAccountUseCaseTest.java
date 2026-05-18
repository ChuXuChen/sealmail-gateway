package com.sealmail.app.usecase.auth;

import com.sealmail.app.dto.request.CreateUserRequest;
import com.sealmail.app.dto.request.UpdateUserRequest;
import com.sealmail.app.dto.request.UserChangePasswordRequest;
import com.sealmail.app.dto.request.UserProfileUpdateRequest;
import com.sealmail.app.dto.request.UserResetPasswordRequest;
import com.sealmail.app.dto.response.UserSummaryResponse;
import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.audit.AuditLog;
import com.sealmail.domain.audit.AuditLogRepository;
import com.sealmail.domain.audit.AuditLogType;
import com.sealmail.domain.audit.AuditService;
import com.sealmail.domain.security.PasswordEncoder;
import com.sealmail.domain.user.UserAccount;
import com.sealmail.domain.user.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAccountUseCaseTest {

    private InMemoryUserAccountRepository userRepository;
    private TestPasswordEncoder passwordEncoder;
    private RecordingAuditLogRepository auditRepository;
    private UserAccountUseCase useCase;
    private LocalAuthService localAuthService;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserAccountRepository();
        passwordEncoder = new TestPasswordEncoder();
        auditRepository = new RecordingAuditLogRepository();
        AuditService auditService = new AuditService(auditRepository);
        useCase = new UserAccountUseCase(userRepository, passwordEncoder, auditService);
        localAuthService = new LocalAuthService(userRepository, passwordEncoder, auditService);

        saveUser("super", "root", "root@example.com", "root-password", Set.of("SUPER_ADMIN"), Set.of());
        saveUser("user", "bob", "bob@example.com", "bob-password", Set.of("USER"), Set.of("example.com"));
    }

    @Test
    void superAdminCanCreateEditDisableEnableUnlockAndResetPassword() {
        UserContext operator = context("super");

        UserSummaryResponse created = useCase.create(new CreateUserRequest(
                "alice",
                "alice@example.com",
                "alice-password",
                List.of("DOMAIN_ADMIN"),
                List.of("example.org")
        ), operator, "127.0.0.1");

        UserAccount alice = userRepository.findById(created.getUserId()).orElseThrow();
        assertThat(alice.getUsername()).isEqualTo("alice");
        assertThat(passwordEncoder.matches("alice-password", alice.getPasswordHash())).isTrue();
        assertThat(auditRepository.types()).contains(AuditLogType.USER_CREATED);

        UserSummaryResponse updated = useCase.update(alice.getId(), new UpdateUserRequest(
                "alice2",
                "alice2@example.com",
                List.of("AUDITOR"),
                List.of("example.net"),
                null,
                null
        ), operator, "127.0.0.1");

        alice = userRepository.findById(created.getUserId()).orElseThrow();
        assertThat(updated.getUsername()).isEqualTo("alice2");
        assertThat(alice.getEmail()).isEqualTo("alice2@example.com");
        assertThat(alice.getRoles()).containsExactly("AUDITOR");
        assertThat(alice.getManagedDomains()).containsExactly("example.net");
        assertThat(alice.getTokenInvalidBefore()).isNotNull();
        assertThat(auditRepository.types()).contains(AuditLogType.USER_UPDATED, AuditLogType.USER_ROLE_CHANGED);

        alice.lock();
        userRepository.save(alice);
        useCase.unlock(alice.getId(), operator, "127.0.0.1");
        assertThat(userRepository.findById(alice.getId()).orElseThrow().isLocked()).isFalse();
        assertThat(auditRepository.types()).contains(AuditLogType.USER_UNLOCKED);

        useCase.disable(alice.getId(), operator, "127.0.0.1");
        assertThat(userRepository.findById(alice.getId()).orElseThrow().isActive()).isFalse();

        useCase.enable(alice.getId(), operator, "127.0.0.1");
        assertThat(userRepository.findById(alice.getId()).orElseThrow().isActive()).isTrue();

        useCase.resetPassword(alice.getId(), new UserResetPasswordRequest("new-password"), operator, "127.0.0.1");
        assertThat(passwordEncoder.matches("new-password", userRepository.findById(alice.getId()).orElseThrow().getPasswordHash()))
                .isTrue();
        assertThat(auditRepository.types()).contains(AuditLogType.USER_PASSWORD_CHANGED);
    }

    @Test
    void ordinaryUserCanUpdateOwnProfileAndPassword() {
        UserContext bob = context("user");

        useCase.updateProfile(new UserProfileUpdateRequest("robert", "robert@example.com"), bob, "127.0.0.1");
        UserAccount updated = userRepository.findById("user").orElseThrow();
        assertThat(updated.getUsername()).isEqualTo("robert");
        assertThat(updated.getEmail()).isEqualTo("robert@example.com");
        assertThat(updated.getTokenInvalidBefore()).isNotNull();
        assertThat(auditRepository.types()).contains(AuditLogType.USER_UPDATED);

        assertThat(localAuthService.authenticate("robert", "bob-password", "127.0.0.1").userId())
                .isEqualTo("user");
        assertThatThrownBy(() -> localAuthService.authenticate("bob", "bob-password", "127.0.0.1"))
                .isInstanceOf(BusinessException.class);

        useCase.changePassword(new UserChangePasswordRequest("bob-password", "changed-password"), context("user"), "127.0.0.1");
        assertThat(passwordEncoder.matches("changed-password", userRepository.findById("user").orElseThrow().getPasswordHash()))
                .isTrue();
    }

    @Test
    void listSupportsStatusFiltersAndRequiresSuperAdmin() {
        useCase.disable("user", context("super"), "127.0.0.1");

        assertThat(useCase.list(context("super"), "active"))
                .extracting(UserSummaryResponse::getUserId)
                .containsExactly("super");
        assertThat(useCase.list(context("super"), "disabled"))
                .extracting(UserSummaryResponse::getUserId)
                .containsExactly("user");
        assertThat(useCase.list(context("super"), "all"))
                .extracting(UserSummaryResponse::getUserId)
                .containsExactly("user", "super");

        assertThatThrownBy(() -> useCase.list(context("user"), "all"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有超级管理员");
    }

    @Test
    void duplicateUsernameOrEmailIsRejected() {
        UserContext operator = context("super");

        assertThatThrownBy(() -> useCase.create(new CreateUserRequest(
                "bob",
                "new@example.com",
                "password",
                List.of("USER"),
                List.of()
        ), operator, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名已存在");

        assertThatThrownBy(() -> useCase.update("user", new UpdateUserRequest(
                "bob2",
                "root@example.com",
                List.of("USER"),
                List.of(),
                null,
                null
        ), operator, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("邮箱已存在");
    }

    @Test
    void disabledUserCannotLoginAndCanLoginAfterEnable() {
        useCase.disable("user", context("super"), "127.0.0.1");

        assertThatThrownBy(() -> localAuthService.authenticate("bob", "bob-password", "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号已禁用");

        useCase.enable("user", context("super"), "127.0.0.1");
        assertThat(localAuthService.authenticate("bob", "bob-password", "127.0.0.1").userId())
                .isEqualTo("user");
    }

    @Test
    void protectsCurrentAndLastSuperAdmin() {
        assertThatThrownBy(() -> useCase.disable("super", context("super"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能停用当前登录用户");

        assertThatThrownBy(() -> useCase.update("super", new UpdateUserRequest(
                "root",
                "root@example.com",
                List.of("USER"),
                List.of(),
                null,
                null
        ), context("super"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能移除自己的 SUPER_ADMIN");

        saveUser("super2", "root2", "root2@example.com", "root2-password", Set.of("SUPER_ADMIN"), Set.of());
        useCase.update("super", new UpdateUserRequest(
                "root",
                "root@example.com",
                List.of("USER"),
                List.of(),
                null,
                null
        ), context("super2"), "127.0.0.1");
        assertThat(userRepository.findById("super").orElseThrow().getRoles()).containsExactly("USER");
        useCase.update("super", new UpdateUserRequest(
                "root",
                "root@example.com",
                List.of("SUPER_ADMIN"),
                List.of(),
                null,
                null
        ), context("super2"), "127.0.0.1");

        userRepository.remove("super2");
        saveUser("operator", "operator", "operator@example.com", "operator-password", Set.of("SUPER_ADMIN"), Set.of());
        useCase.disable("operator", context("super"), "127.0.0.1");

        assertThatThrownBy(() -> useCase.disable("super", context("operator"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最后一个超级管理员");

        assertThatThrownBy(() -> useCase.update("super", new UpdateUserRequest(
                "root",
                "root@example.com",
                List.of("USER"),
                List.of(),
                null,
                null
        ), context("operator"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最后一个超级管理员");
    }

    private UserAccount saveUser(String id,
                                 String username,
                                 String email,
                                 String password,
                                 Set<String> roles,
                                 Set<String> managedDomains) {
        UserAccount user = UserAccount.create(
                id,
                username,
                email,
                passwordEncoder.encode(password),
                roles,
                managedDomains
        );
        userRepository.save(user);
        return user;
    }

    private UserContext context(String userId) {
        UserAccount user = userRepository.findById(userId).orElseThrow();
        return UserContext.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(new LinkedHashSet<>(user.getRoles()))
                .managedDomains(new LinkedHashSet<>(user.getManagedDomains()))
                .build();
    }

    private static final class TestPasswordEncoder implements PasswordEncoder {

        @Override
        public String encode(String rawPassword) {
            return "{test}" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String encodedPassword) {
            return encode(rawPassword).equals(encodedPassword);
        }
    }

    private static final class InMemoryUserAccountRepository implements UserAccountRepository {

        private final Map<String, UserAccount> users = new LinkedHashMap<>();

        @Override
        public UserAccount save(UserAccount userAccount) {
            users.put(userAccount.getId(), userAccount);
            return userAccount;
        }

        @Override
        public Optional<UserAccount> findById(String id) {
            return Optional.ofNullable(users.get(id));
        }

        @Override
        public Optional<UserAccount> findByUsername(String username) {
            if (username == null) {
                return Optional.empty();
            }
            return users.values().stream()
                    .filter(user -> user.getUsername().equalsIgnoreCase(username))
                    .findFirst();
        }

        @Override
        public Optional<UserAccount> findByEmail(String email) {
            if (email == null) {
                return Optional.empty();
            }
            return users.values().stream()
                    .filter(user -> user.getEmail().equalsIgnoreCase(email))
                    .findFirst();
        }

        @Override
        public boolean existsByUsername(String username) {
            return findByUsername(username).isPresent();
        }

        @Override
        public boolean existsByEmail(String email) {
            return findByEmail(email).isPresent();
        }

        @Override
        public List<UserAccount> findAll() {
            return users.values().stream()
                    .sorted(Comparator.comparing(UserAccount::getUsername))
                    .toList();
        }

        private void remove(String id) {
            users.remove(id);
        }
    }

    private static final class RecordingAuditLogRepository implements AuditLogRepository {

        private final List<AuditLog> logs = new ArrayList<>();

        @Override
        public AuditLog save(AuditLog auditLog) {
            logs.add(auditLog);
            return auditLog;
        }

        private List<AuditLogType> types() {
            return logs.stream().map(AuditLog::getType).toList();
        }

        @Override
        public Optional<AuditLog> findById(String id) {
            return logs.stream().filter(log -> log.getId().equals(id)).findFirst();
        }

        @Override
        public List<AuditLog> findByType(AuditLogType type, int page, int size) {
            return logs.stream().filter(log -> log.getType() == type).toList();
        }

        @Override
        public List<AuditLog> findByUserId(String userId, int page, int size) {
            return logs.stream().filter(log -> userId.equals(log.getUserId())).toList();
        }

        @Override
        public List<AuditLog> findByResource(String resourceType, String resourceId, int page, int size) {
            return logs.stream()
                    .filter(log -> resourceType.equals(log.getResourceType()) && resourceId.equals(log.getResourceId()))
                    .toList();
        }

        @Override
        public List<AuditLog> findByTimeRange(Instant startTime, Instant endTime, int page, int size) {
            return List.copyOf(logs);
        }

        @Override
        public List<AuditLog> findAll(int page, int size) {
            return List.copyOf(logs);
        }

        @Override
        public List<AuditLog> search(List<AuditLogType> types, Boolean success, int page, int size) {
            return List.copyOf(logs);
        }

        @Override
        public long count() {
            return logs.size();
        }

        @Override
        public long countByType(AuditLogType type) {
            return logs.stream().filter(log -> log.getType() == type).count();
        }

        @Override
        public long countByUserId(String userId) {
            return logs.stream().filter(log -> userId.equals(log.getUserId())).count();
        }

        @Override
        public long countByResource(String resourceType, String resourceId) {
            return findByResource(resourceType, resourceId, 1, Integer.MAX_VALUE).size();
        }

        @Override
        public long countByTimeRange(Instant startTime, Instant endTime) {
            return logs.size();
        }

        @Override
        public long countSearch(List<AuditLogType> types, Boolean success) {
            return logs.size();
        }
    }
}
