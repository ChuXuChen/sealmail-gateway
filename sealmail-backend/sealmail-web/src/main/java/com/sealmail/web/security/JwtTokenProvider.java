package com.sealmail.web.security;

import com.sealmail.app.security.UserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long tokenValidityInMilliseconds;
    private final long tokenValidityInSeconds;

    public JwtTokenProvider(
            @Value("${jwt.secret:sealmail-super-secret-key-for-jwt-token-signing-2026}") String secret,
            @Value("${jwt.token-validity-in-seconds:86400}") long tokenValidityInSeconds) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.tokenValidityInSeconds = tokenValidityInSeconds;
        this.tokenValidityInMilliseconds = tokenValidityInSeconds * 1000;
    }

    public String createToken(String userId,
                              String username,
                              String email,
                              List<String> roles,
                              List<String> managedDomains) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + tokenValidityInMilliseconds);
        List<String> normalizedRoles = roles != null && !roles.isEmpty() ? roles : List.of("USER");
        List<String> normalizedDomains = managedDomains != null ? managedDomains : List.of();

        return Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .claim("email", email)
                .claim("roles", normalizedRoles)
                .claim("role", normalizedRoles.getFirst())
                .claim("domains", normalizedDomains)
                .claim("issuedAtMillis", now.getTime())
                .issuedAt(now)
                .expiration(validity)
                .signWith(secretKey)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UserContext getUserContext(String token) {
        Claims claims = parseClaims(token);
        return UserContext.builder()
                .userId(claims.getSubject())
                .username(claims.get("username", String.class))
                .email(claims.get("email", String.class))
                .roles(readStringSet(claims, "roles", claims.get("role", String.class)))
                .managedDomains(readStringSet(claims, "domains", null))
                .build();
    }

    public Instant getIssuedAt(String token) {
        Claims claims = parseClaims(token);
        Long issuedAtMillis = claims.get("issuedAtMillis", Long.class);
        if (issuedAtMillis != null) {
            return Instant.ofEpochMilli(issuedAtMillis);
        }
        Date issuedAt = claims.getIssuedAt();
        return issuedAt == null ? null : issuedAt.toInstant();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public long getTokenValidityInSeconds() {
        return tokenValidityInSeconds;
    }

    private Set<String> readStringSet(Claims claims, String key, String fallbackSingleValue) {
        Object raw = claims.get(key);
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    String value = item.toString().trim();
                    if (!value.isBlank()) {
                        values.add(value.toUpperCase(Locale.ROOT));
                    }
                }
            }
        }
        if (values.isEmpty() && fallbackSingleValue != null && !fallbackSingleValue.isBlank()) {
            values.add(fallbackSingleValue.toUpperCase(Locale.ROOT));
        }
        return values;
    }
}
