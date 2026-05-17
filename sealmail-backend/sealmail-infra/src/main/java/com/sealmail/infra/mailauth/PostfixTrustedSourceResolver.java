package com.sealmail.infra.mailauth;

import com.sealmail.domain.mailauth.MailAuthPolicy;
import com.sealmail.domain.mailauth.MailSourceIdentity;
import com.sealmail.domain.mailauth.TrustedMailSourcePort;
import com.sealmail.domain.mailauth.TrustedProxyMode;
import com.sealmail.infra.config.properties.MailAuthProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

@Component
public class PostfixTrustedSourceResolver implements TrustedMailSourcePort {

    private final MailAuthProperties properties;

    public PostfixTrustedSourceResolver(MailAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public MailSourceIdentity resolve(byte[] rawContent, MailSourceIdentity candidate, MailAuthPolicy policy) {
        if (candidate == null) {
            return null;
        }
        TrustedProxyMode mode = policy != null ? policy.trustedProxyMode() : TrustedProxyMode.DISABLED;
        if (mode == TrustedProxyMode.DISABLED) {
            return withDetail(candidate, "trusted source override disabled");
        }
        if (mode == TrustedProxyMode.XFORWARD) {
            return withDetail(candidate, "XFORWARD source override is not exposed by the SMTP adapter yet");
        }
        if (!trustedRelay(candidate.sourceIp())) {
            return withDetail(candidate, "remote source is not a trusted relay");
        }
        String originalIp = originalIpFromHeaders(rawContent);
        if (!hasText(originalIp)) {
            return withDetail(candidate, "trusted relay did not provide an original client IP header");
        }
        return new MailSourceIdentity(
                originalIp,
                candidate.envelopeFromDomain(),
                candidate.headerFromDomain(),
                candidate.helo(),
                true,
                "source IP resolved from trusted relay header");
    }

    private String originalIpFromHeaders(byte[] rawContent) {
        if (rawContent == null || rawContent.length == 0) {
            return null;
        }
        try {
            MimeMessage message = new MimeMessage(
                    Session.getInstance(new Properties()),
                    new ByteArrayInputStream(rawContent));
            for (String headerName : properties.getTrustedSource().getOriginalIpHeaders()) {
                String[] values = message.getHeader(headerName);
                if (values == null || values.length == 0) {
                    continue;
                }
                for (String value : values) {
                    String ip = firstValidIp(value);
                    if (ip != null) {
                        return ip;
                    }
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private String firstValidIp(String value) {
        if (value == null) {
            return null;
        }
        for (String token : value.split(",")) {
            String candidate = token.trim();
            if (candidate.startsWith("[") && candidate.endsWith("]")) {
                candidate = candidate.substring(1, candidate.length() - 1);
            }
            if (validIp(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean trustedRelay(String sourceIp) {
        if (!validIp(sourceIp)) {
            return false;
        }
        List<String> cidrs = properties.getTrustedSource().getTrustedRelayCidrs();
        if (cidrs == null || cidrs.isEmpty()) {
            return false;
        }
        return cidrs.stream().anyMatch(cidr -> contains(cidr, sourceIp));
    }

    private boolean contains(String cidr, String ip) {
        if (!hasText(cidr)) {
            return false;
        }
        String trimmed = cidr.trim();
        if (!trimmed.contains("/")) {
            return trimmed.equalsIgnoreCase(ip);
        }
        try {
            String[] parts = trimmed.split("/", 2);
            InetAddress network = InetAddress.getByName(parts[0]);
            InetAddress address = InetAddress.getByName(ip);
            byte[] networkBytes = network.getAddress();
            byte[] addressBytes = address.getAddress();
            if (networkBytes.length != addressBytes.length) {
                return false;
            }
            int prefix = Integer.parseInt(parts[1]);
            int maxPrefix = networkBytes.length * 8;
            if (prefix < 0 || prefix > maxPrefix) {
                return false;
            }
            BigInteger networkInt = new BigInteger(1, networkBytes);
            BigInteger addressInt = new BigInteger(1, addressBytes);
            BigInteger mask = BigInteger.ONE.shiftLeft(maxPrefix).subtract(BigInteger.ONE)
                    .shiftRight(prefix)
                    .not()
                    .and(BigInteger.ONE.shiftLeft(maxPrefix).subtract(BigInteger.ONE));
            return networkInt.and(mask).equals(addressInt.and(mask));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean validIp(String value) {
        if (!hasText(value)) {
            return false;
        }
        try {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            InetAddress.getByName(normalized);
            return normalized.contains(".") || normalized.contains(":");
        } catch (Exception e) {
            return false;
        }
    }

    private MailSourceIdentity withDetail(MailSourceIdentity candidate, String detail) {
        return new MailSourceIdentity(
                candidate.sourceIp(),
                candidate.envelopeFromDomain(),
                candidate.headerFromDomain(),
                candidate.helo(),
                candidate.trustedProxyOverride(),
                detail);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
