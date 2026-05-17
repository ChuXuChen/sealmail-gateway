package com.sealmail.edge.routing;

import com.sealmail.edge.config.EdgeConfig;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class DomainRouteResolver {
    private final List<EdgeConfig.Route> routes;

    public DomainRouteResolver(List<EdgeConfig.Route> routes) {
        this.routes = routes.stream()
                .sorted(Comparator.comparingInt((EdgeConfig.Route route) -> route.domainPattern().length()).reversed())
                .toList();
    }

    public Optional<MailRoute> resolve(List<String> recipients) {
        for (String recipient : recipients) {
            String domain = domainOf(recipient);
            if (domain == null) {
                continue;
            }
            Optional<MailRoute> route = resolveDomain(domain);
            if (route.isPresent()) {
                return route;
            }
        }
        return Optional.empty();
    }

    Optional<MailRoute> resolveDomain(String domain) {
        String normalized = domain.toLowerCase(Locale.ROOT);
        for (EdgeConfig.Route route : routes) {
            if (matches(route.domainPattern(), normalized)) {
                return Optional.of(new MailRoute(route.host(), route.port(), route.security()));
            }
        }
        return Optional.empty();
    }

    private static boolean matches(String pattern, String domain) {
        String normalized = pattern.toLowerCase(Locale.ROOT);
        if ("*".equals(normalized)) {
            return true;
        }
        if (normalized.startsWith("*.")) {
            String suffix = normalized.substring(1);
            return domain.endsWith(suffix) && domain.length() > suffix.length();
        }
        if (normalized.startsWith(".")) {
            String suffix = normalized.substring(1);
            return domain.equals(suffix) || domain.endsWith(normalized);
        }
        return domain.equals(normalized);
    }

    private static String domainOf(String address) {
        if (address == null) {
            return null;
        }
        String clean = address.trim();
        if (clean.startsWith("<") && clean.endsWith(">")) {
            clean = clean.substring(1, clean.length() - 1);
        }
        int at = clean.lastIndexOf('@');
        if (at < 0 || at == clean.length() - 1) {
            return null;
        }
        return clean.substring(at + 1);
    }
}
