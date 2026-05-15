package com.sealmail.web.security;

import com.sealmail.app.security.CurrentUserResolver;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.user.UserAccount;
import com.sealmail.domain.user.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserAccountRepository userAccountRepository;
    private final CurrentUserResolver currentUserResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            UserContext tokenUser = jwtTokenProvider.getUserContext(token);
            UserAccount currentUser = userAccountRepository.findById(tokenUser.getUserId()).orElse(null);

            if (isTokenUsable(token, currentUser)) {
                UserContext userContext = currentUserResolver.toUserContext(currentUser);
                List<SimpleGrantedAuthority> authorities = userContext.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .collect(Collectors.toList());

                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        userContext,
                        null,
                        authorities
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isTokenUsable(String token, UserAccount userAccount) {
        if (userAccount == null || !userAccount.isActive() || !userAccount.canAuthenticate()) {
            return false;
        }
        Instant tokenInvalidBefore = userAccount.getTokenInvalidBefore();
        if (tokenInvalidBefore == null) {
            return true;
        }
        Instant issuedAt = jwtTokenProvider.getIssuedAt(token);
        return issuedAt != null && !issuedAt.isBefore(tokenInvalidBefore);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
