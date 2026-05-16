package com.sealmail.infra.security;

import com.sealmail.domain.security.AuthTokenPort;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Component
public class JjwtAuthTokenPort implements AuthTokenPort {

    private final SecretKey secretKey;
    private final long tokenValidityInSeconds;

    public JjwtAuthTokenPort(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.token-validity-in-seconds:86400}") long tokenValidityInSeconds) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.tokenValidityInSeconds = tokenValidityInSeconds;
    }

    @Override
    public IssuedToken issue(TokenSubject subject) {
        Instant issuedAt = subject.issuedAt() != null ? subject.issuedAt() : Instant.now();
        Date issuedAtDate = Date.from(issuedAt);
        Date expiration = Date.from(issuedAt.plusSeconds(tokenValidityInSeconds));
        List<String> roles = subject.roles() != null && !subject.roles().isEmpty()
                ? subject.roles()
                : List.of("USER");
        List<String> domains = subject.managedDomains() != null ? subject.managedDomains() : List.of();

        String token = Jwts.builder()
                .subject(subject.userId())
                .claim("username", subject.username())
                .claim("email", subject.email())
                .claim("roles", roles)
                .claim("role", roles.getFirst())
                .claim("domains", domains)
                .claim("issuedAtMillis", issuedAt.toEpochMilli())
                .issuedAt(issuedAtDate)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
        return new IssuedToken(token, tokenValidityInSeconds);
    }

    @Override
    public Optional<TokenSubject> read(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long issuedAtMillis = claims.get("issuedAtMillis", Long.class);
            Instant issuedAt = issuedAtMillis != null
                    ? Instant.ofEpochMilli(issuedAtMillis)
                    : claims.getIssuedAt() == null ? null : claims.getIssuedAt().toInstant();
            return Optional.of(new TokenSubject(
                    claims.getSubject(),
                    claims.get("username", String.class),
                    claims.get("email", String.class),
                    List.copyOf(readStringSet(claims, "roles", claims.get("role", String.class))),
                    List.copyOf(readStringSet(claims, "domains", null)),
                    issuedAt));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
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
