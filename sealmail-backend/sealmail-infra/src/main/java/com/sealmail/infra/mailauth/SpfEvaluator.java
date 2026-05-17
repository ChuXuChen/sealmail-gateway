package com.sealmail.infra.mailauth;

import com.sealmail.domain.mailauth.AuthenticationMechanism;
import com.sealmail.domain.mailauth.AuthenticationMechanismResult;
import com.sealmail.domain.mailauth.AuthenticationResult;
import com.sealmail.infra.config.properties.MailAuthProperties;
import com.sealmail.infra.dns.DnsTxtResolver;

import java.math.BigInteger;
import java.net.InetAddress;
import java.util.List;
import java.util.Locale;

final class SpfEvaluator {

    private final DnsTxtResolver dns;
    private final MailAuthProperties properties;

    SpfEvaluator(DnsTxtResolver dns, MailAuthProperties properties) {
        this.dns = dns;
        this.properties = properties;
    }

    AuthenticationMechanismResult verify(String ipAddress, String senderDomain) {
        if (!properties.getSpf().isEnabled()) {
            return result(AuthenticationResult.NONE, senderDomain, ipAddress, "SPF disabled");
        }
        if (isBlank(senderDomain)) {
            return result(AuthenticationResult.NONE, null, ipAddress, "SPF sender domain missing");
        }
        if (properties.isSkipPrivateRelay() && isPrivateAddress(ipAddress)) {
            return result(AuthenticationResult.NONE, senderDomain, ipAddress, "SPF skipped for private relay source");
        }
        try {
            AuthenticationResult spf = evaluate(ipAddress, senderDomain.toLowerCase(Locale.ROOT), 0);
            return result(spf, senderDomain, ipAddress, "SPF evaluated");
        } catch (IllegalArgumentException e) {
            return result(AuthenticationResult.PERMERROR, senderDomain, ipAddress, e.getMessage());
        } catch (Exception e) {
            return result(AuthenticationResult.TEMPERROR, senderDomain, ipAddress, e.getClass().getSimpleName());
        }
    }

    private AuthenticationResult evaluate(String ipAddress, String domain, int depth) {
        if (depth > properties.getSpf().getMaxDnsLookups()) {
            return AuthenticationResult.PERMERROR;
        }
        List<String> records = dns.txt(domain).stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith("v=spf1"))
                .toList();
        if (records.isEmpty()) {
            return AuthenticationResult.NONE;
        }
        if (records.size() > 1) {
            return AuthenticationResult.PERMERROR;
        }

        String redirect = null;
        String[] terms = records.get(0).split("\\s+");
        for (int i = 1; i < terms.length; i++) {
            String term = terms[i].trim();
            if (term.isBlank()) {
                continue;
            }
            if (term.toLowerCase(Locale.ROOT).startsWith("redirect=")) {
                redirect = term.substring("redirect=".length());
                continue;
            }
            char qualifier = qualifier(term);
            String mechanism = hasQualifier(term) ? term.substring(1) : term;
            String mechanismKey = mechanism.toLowerCase(Locale.ROOT);
            if (mechanismKey.equals("all")) {
                return resultForQualifier(qualifier);
            }
            if (mechanismKey.startsWith("ip4:") || mechanismKey.startsWith("ip6:")) {
                if (ipMatchesCidr(ipAddress, mechanism.substring(4))) {
                    return resultForQualifier(qualifier);
                }
                continue;
            }
            if (mechanismKey.equals("a") || mechanismKey.startsWith("a:") || mechanismKey.startsWith("a/")) {
                if (matchesA(ipAddress, domain, mechanism)) {
                    return resultForQualifier(qualifier);
                }
                continue;
            }
            if (mechanismKey.equals("mx") || mechanismKey.startsWith("mx:") || mechanismKey.startsWith("mx/")) {
                if (matchesMx(ipAddress, domain, mechanism)) {
                    return resultForQualifier(qualifier);
                }
                continue;
            }
            if (mechanismKey.startsWith("include:")) {
                AuthenticationResult include = evaluate(ipAddress, mechanism.substring("include:".length()), depth + 1);
                if (include == AuthenticationResult.PASS) {
                    return resultForQualifier(qualifier);
                }
                if (include == AuthenticationResult.TEMPERROR || include == AuthenticationResult.PERMERROR) {
                    return include;
                }
                continue;
            }
            if (mechanismKey.startsWith("exists:")) {
                String existsDomain = mechanism.substring("exists:".length());
                if (!dns.a(existsDomain).isEmpty() || !dns.aaaa(existsDomain).isEmpty()) {
                    return resultForQualifier(qualifier);
                }
            }
        }
        if (!isBlank(redirect)) {
            return evaluate(ipAddress, redirect, depth + 1);
        }
        return AuthenticationResult.NEUTRAL;
    }

    private boolean matchesA(String ipAddress, String currentDomain, String mechanism) {
        DomainAndCidr domainAndCidr = domainAndCidr(currentDomain, mechanism, "a");
        List<String> addresses = ipAddress.contains(":") ? dns.aaaa(domainAndCidr.domain()) : dns.a(domainAndCidr.domain());
        return addresses.stream().anyMatch(address -> ipMatchesCidr(ipAddress, address + domainAndCidr.cidrSuffix()));
    }

    private boolean matchesMx(String ipAddress, String currentDomain, String mechanism) {
        DomainAndCidr domainAndCidr = domainAndCidr(currentDomain, mechanism, "mx");
        for (String mx : dns.mx(domainAndCidr.domain())) {
            String[] parts = mx.split("\\s+");
            String host = parts[parts.length - 1].replaceAll("\\.$", "");
            List<String> addresses = ipAddress.contains(":") ? dns.aaaa(host) : dns.a(host);
            if (addresses.stream().anyMatch(address -> ipMatchesCidr(ipAddress, address + domainAndCidr.cidrSuffix()))) {
                return true;
            }
        }
        return false;
    }

    private DomainAndCidr domainAndCidr(String currentDomain, String mechanism, String prefix) {
        String rest = mechanism.length() > prefix.length() ? mechanism.substring(prefix.length()) : "";
        String domain = currentDomain;
        String cidr = "";
        if (rest.startsWith(":")) {
            rest = rest.substring(1);
            int slash = rest.indexOf('/');
            domain = slash >= 0 ? rest.substring(0, slash) : rest;
            cidr = slash >= 0 ? rest.substring(slash) : "";
        } else if (rest.startsWith("/")) {
            cidr = rest;
        }
        return new DomainAndCidr(domain, cidr);
    }

    private boolean ipMatchesCidr(String ipAddress, String cidrExpression) {
        try {
            String[] parts = cidrExpression.split("/", 2);
            InetAddress network = InetAddress.getByName(parts[0]);
            InetAddress address = InetAddress.getByName(ipAddress);
            byte[] networkBytes = network.getAddress();
            byte[] addressBytes = address.getAddress();
            if (networkBytes.length != addressBytes.length) {
                return false;
            }
            int prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : networkBytes.length * 8;
            int maxPrefix = networkBytes.length * 8;
            if (prefix < 0 || prefix > maxPrefix) {
                return false;
            }
            BigInteger networkInt = new BigInteger(1, networkBytes);
            BigInteger addressInt = new BigInteger(1, addressBytes);
            BigInteger allOnes = BigInteger.ONE.shiftLeft(maxPrefix).subtract(BigInteger.ONE);
            BigInteger mask = allOnes.shiftRight(prefix).not().and(allOnes);
            return networkInt.and(mask).equals(addressInt.and(mask));
        } catch (Exception e) {
            return false;
        }
    }

    private char qualifier(String term) {
        return hasQualifier(term) ? term.charAt(0) : '+';
    }

    private boolean hasQualifier(String term) {
        return !term.isBlank() && "+-~?".indexOf(term.charAt(0)) >= 0;
    }

    private AuthenticationResult resultForQualifier(char qualifier) {
        return switch (qualifier) {
            case '-' -> AuthenticationResult.FAIL;
            case '~' -> AuthenticationResult.SOFTFAIL;
            case '?' -> AuthenticationResult.NEUTRAL;
            default -> AuthenticationResult.PASS;
        };
    }

    private boolean isPrivateAddress(String ipAddress) {
        if (isBlank(ipAddress)) {
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

    private AuthenticationMechanismResult result(AuthenticationResult result,
                                                 String domain,
                                                 String ipAddress,
                                                 String detail) {
        return new AuthenticationMechanismResult(AuthenticationMechanism.SPF, result, domain, ipAddress, detail);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record DomainAndCidr(String domain, String cidrSuffix) {
    }
}
