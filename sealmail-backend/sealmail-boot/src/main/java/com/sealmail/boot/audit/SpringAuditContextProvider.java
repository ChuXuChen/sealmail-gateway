package com.sealmail.boot.audit;

import com.sealmail.app.security.UserContext;
import com.sealmail.domain.audit.AuditContext;
import com.sealmail.domain.audit.AuditContextProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@Primary
public class SpringAuditContextProvider implements AuditContextProvider {

    @Override
    public AuditContext currentContext() {
        UserContext user = currentUser();
        return new AuditContext(
                user != null ? user.getUserId() : null,
                user != null ? user.getUsername() : null,
                currentIpAddress());
    }

    private UserContext currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof UserContext userContext ? userContext : null;
    }

    private String currentIpAddress() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }
}
