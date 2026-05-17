package com.sealmail.app.usecase.mailauth;

import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.mailauth.DmarcAlignmentMode;
import com.sealmail.domain.mailauth.DmarcPolicyMode;
import com.sealmail.domain.mailauth.MailAuthFailureAction;
import com.sealmail.domain.mailauth.TrustedProxyMode;

final class MailAuthUseCaseSupport {

    private MailAuthUseCaseSupport() {
    }

    static void requireAdmin(UserContext user, String message) {
        if (user == null || !user.isAdmin()) {
            throw BusinessException.forbidden(message);
        }
    }

    static TrustedProxyMode trustedProxyMode(String value, TrustedProxyMode fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return TrustedProxyMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("可信来源模式无效: " + value);
        }
    }

    static MailAuthFailureAction failureAction(String value, MailAuthFailureAction fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return MailAuthFailureAction.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("邮件认证失败处理无效: " + value);
        }
    }

    static DmarcPolicyMode dmarcPolicy(String value, DmarcPolicyMode fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return DmarcPolicyMode.fromTag(value);
    }

    static DmarcAlignmentMode alignmentMode(String value, DmarcAlignmentMode fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return DmarcAlignmentMode.fromTag(value);
    }
}
