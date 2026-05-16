package com.sealmail.infra.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "sealmail.auth")
public class AuthProperties {

    private List<UserRecord> users = new ArrayList<>();

    public List<UserRecord> getUsers() {
        return users;
    }

    public void setUsers(List<UserRecord> users) {
        this.users = users;
    }

    public static class UserRecord {
        private String userId;
        private String username;
        private String email;
        private String passwordSecretRef;
        private List<String> roles = new ArrayList<>();
        private List<String> managedDomains = new ArrayList<>();

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPasswordSecretRef() {
            return passwordSecretRef;
        }

        public void setPasswordSecretRef(String passwordSecretRef) {
            this.passwordSecretRef = passwordSecretRef;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }

        public List<String> getManagedDomains() {
            return managedDomains;
        }

        public void setManagedDomains(List<String> managedDomains) {
            this.managedDomains = managedDomains;
        }
    }
}
