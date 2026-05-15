package com.sealmail.app.security;

import com.sealmail.app.exception.SecurityException;
import org.springframework.stereotype.Component;

@Component
public class PermissionChecker {

    public void checkAuthenticated(UserContext user) {
        if (user == null) {
            throw SecurityException.unauthorized("User not authenticated");
        }
    }

    public void checkCanManageCa(UserContext user) {
        checkAuthenticated(user);
        if (!user.canManageCa()) {
            throw SecurityException.accessDenied("User does not have permission to manage CA resources");
        }
    }

    public void checkCanReviewCsr(UserContext user) {
        checkAuthenticated(user);
        if (!user.canReviewCsr()) {
            throw SecurityException.accessDenied("User does not have permission to review CSR requests");
        }
    }

    public void checkCanManageCertificates(UserContext user, String ownerDomain) {
        checkAuthenticated(user);
        if (!user.canManageDomain(ownerDomain)) {
            throw SecurityException.accessDenied(
                    "User does not have permission to manage certificates for domain: " + ownerDomain);
        }
    }

    public void checkCanViewCertificates(UserContext user, String ownerDomain) {
        checkAuthenticated(user);
        if (!user.canViewDomain(ownerDomain)) {
            throw SecurityException.accessDenied(
                    "User does not have permission to view certificates for domain: " + ownerDomain);
        }
    }

    public void checkCanViewAllCertificates(UserContext user) {
        checkAuthenticated(user);
        if (!user.canViewAllCertificates()) {
            throw SecurityException.accessDenied("User does not have permission to view all certificates");
        }
    }

    public void checkCanManageQuarantine(UserContext user) {
        checkAuthenticated(user);
        if (!user.isAdmin()) {
            throw SecurityException.accessDenied(
                    "Only administrators can manage quarantine items");
        }
    }

    public void checkCanViewQuarantine(UserContext user) {
        checkAuthenticated(user);
        if (!user.isAdmin() && !user.isAuditor()) {
            throw SecurityException.accessDenied("Only administrators or auditors can view quarantine items");
        }
    }
}
