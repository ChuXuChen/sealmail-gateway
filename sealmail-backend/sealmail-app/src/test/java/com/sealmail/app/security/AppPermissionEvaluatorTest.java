package com.sealmail.app.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AppPermissionEvaluatorTest {

    private final AppPermissionEvaluator evaluator = new AppPermissionEvaluator();

    @Test
    void runtimePolicyPermissionCanBeGrantedWithoutCaManagement() {
        UserContext user = UserContext.builder()
                .roles(Set.of("RUNTIME_POLICY_ADMIN"))
                .build();

        assertThat(evaluator.hasPermission(user, AppPermission.MANAGE_RUNTIME_POLICY)).isTrue();
        assertThat(evaluator.hasPermission(user, AppPermission.MANAGE_CA)).isFalse();
    }

    @Test
    void mailToolPermissionCanBeGrantedWithoutCaManagement() {
        UserContext user = UserContext.builder()
                .roles(Set.of("MAIL_OPERATOR"))
                .build();

        assertThat(evaluator.hasPermission(user, AppPermission.OPERATE_MAIL_TOOLS)).isTrue();
        assertThat(evaluator.hasPermission(user, AppPermission.MANAGE_CA)).isFalse();
    }
}
