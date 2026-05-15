package com.sealmail.infra.mail.auth;

import com.sealmail.infra.config.properties.MailAuthProperties;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.List;
import java.util.Locale;

@Component
public class SpfVerifier {

    private final DnsTxtResolver dns;
    private final MailAuthProperties properties;

    public SpfVerifier(DnsTxtResolver dns, MailAuthProperties properties) {
        this.dns = dns;
        this.properties = properties;
    }

    public AuthResult verify(String ipAddress, String senderDomain) {
        if (!properties.getSpf().isEnabled()) {
            return AuthResult.NONE;
        }
        if (senderDomain == null || senderDomain.isBlank()) {
            return AuthResult.NONE;
        }
        if (properties.isSkipPrivateRelay() && isPrivateAddress(ipAddress)) {
            return AuthResult.NONE;
        }
        try {
            return evaluate(ipAddress, senderDomain.toLowerCase(Locale.ROOT), 0);
        } catch (Exception e) {
            return AuthResult.TEMPERROR;
        }
    }

    private AuthResult evaluate(String ipAddress, String domain, int depth) {
        if (depth > properties.getSpf().getMaxDnsLookups()) {
            return AuthResult.PERMERROR;
        }
        String record = dns.txt(domain).stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith("v=spf1"))
                .findFirst()
                .orElse(null);
        if (record == null) {
            return AuthResult.NONE;
        }

        String[] terms = record.split("\\s+");
        for (int i = 1; i < terms.length; i++) {
            String term = terms[i].trim();
            if (term.isBlank()) {
                continue;
            }
            char qualifier = qualifier(term);
            String mechanism = hasQualifier(term) ? term.substring(1) : term;
            String mechanismKey = mechanism.toLowerCase(Locale.ROOT);

            if (mechanismKey.equals("all")) {
                return resultForQualifier(qualifier);
            }
            if (mechanismKey.startsWith("ip4:") && ipAddress.equals(mechanism.substring(4))) {
                return resultForQualifier(qualifier);
            }
            if (mechanismKey.startsWith("ip6:") && ipAddress.equalsIgnoreCase(mechanism.substring(4))) {
                return resultForQualifier(qualifier);
            }
            if (mechanismKey.equals("a") && matchesHostAddress(ipAddress, domain)) {
                return resultForQualifier(qualifier);
            }
            if (mechanismKey.equals("mx") && matchesMxAddress(ipAddress, domain)) {
                return resultForQualifier(qualifier);
            }
            if (mechanismKey.startsWith("include:")) {
                AuthResult include = evaluate(ipAddress, mechanism.substring("include:".length()), depth + 1);
                if (include == AuthResult.PASS) {
                    return resultForQualifier(qualifier);
                }
                if (include == AuthResult.TEMPERROR || include == AuthResult.PERMERROR) {
                    return include;
                }
            }
            if (mechanismKey.startsWith("redirect=")) {
                return evaluate(ipAddress, mechanism.substring("redirect=".length()), depth + 1);
            }
        }
        return AuthResult.NEUTRAL;
    }

    private boolean matchesHostAddress(String ipAddress, String host) {
        List<String> addresses = ipAddress.contains(":") ? dns.aaaa(host) : dns.a(host);
        return addresses.stream().anyMatch(ipAddress::equalsIgnoreCase);
    }

    private boolean matchesMxAddress(String ipAddress, String domain) {
        for (String mx : dns.mx(domain)) {
            String[] parts = mx.split("\\s+");
            String host = parts[parts.length - 1].replaceAll("\\.$", "");
            if (matchesHostAddress(ipAddress, host)) {
                return true;
            }
        }
        return false;
    }

    private char qualifier(String term) {
        return hasQualifier(term) ? term.charAt(0) : '+';
    }

    private boolean hasQualifier(String term) {
        return !term.isBlank() && "+-~?".indexOf(term.charAt(0)) >= 0;
    }

    private AuthResult resultForQualifier(char qualifier) {
        return switch (qualifier) {
            case '-' -> AuthResult.FAIL;
            case '~' -> AuthResult.SOFTFAIL;
            case '?' -> AuthResult.NEUTRAL;
            default -> AuthResult.PASS;
        };
    }

    private boolean isPrivateAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return true;
        }
        try {
            InetAddress address = InetAddress.getByName(ipAddress);
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress();
        } catch (Exception e) {
            return false;
        }
    }
}
