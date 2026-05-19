package com.sealmail.infra.crypto;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.x509.extension.X509ExtensionUtil;

import java.security.cert.X509Certificate;
import java.util.Optional;

final class BcCertificateExtensions {

    Optional<String> subjectKeyIdentifier(X509Certificate cert) {
        try {
            byte[] skiValue = cert.getExtensionValue(Extension.subjectKeyIdentifier.getId());
            if (skiValue == null) {
                return Optional.empty();
            }
            SubjectKeyIdentifier skid = SubjectKeyIdentifier.getInstance(
                    X509ExtensionUtil.fromExtensionValue(skiValue));
            return Optional.of(toHex(skid.getKeyIdentifier()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    Optional<String> authorityKeyIdentifier(X509Certificate cert) {
        try {
            byte[] akiValue = cert.getExtensionValue(Extension.authorityKeyIdentifier.getId());
            if (akiValue == null) {
                return Optional.empty();
            }
            AuthorityKeyIdentifier akid = AuthorityKeyIdentifier.getInstance(
                    X509ExtensionUtil.fromExtensionValue(akiValue));
            return Optional.ofNullable(akid.getKeyIdentifier()).map(this::toHex);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    Optional<String> crlDistributionPointUrl(X509Certificate cert) {
        try {
            byte[] ext = cert.getExtensionValue(Extension.cRLDistributionPoints.getId());
            if (ext == null) {
                return Optional.empty();
            }
            CRLDistPoint distPoint = CRLDistPoint.getInstance(X509ExtensionUtil.fromExtensionValue(ext));
            if (distPoint == null) {
                return Optional.empty();
            }
            for (DistributionPoint dp : distPoint.getDistributionPoints()) {
                DistributionPointName name = dp.getDistributionPoint();
                if (name == null || name.getType() != DistributionPointName.FULL_NAME) {
                    continue;
                }
                GeneralNames generalNames = GeneralNames.getInstance(name.getName());
                for (GeneralName generalName : generalNames.getNames()) {
                    if (generalName.getTagNo() == GeneralName.uniformResourceIdentifier) {
                        return Optional.of(generalName.getName().toString());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    Optional<String> emailAddress(X500Name subject) {
        for (RDN rdn : subject.getRDNs(BCStyle.EmailAddress)) {
            Optional<String> email = readRdn(rdn);
            if (email.isPresent()) {
                return email;
            }
        }
        for (RDN rdn : subject.getRDNs(BCStyle.E)) {
            Optional<String> email = readRdn(rdn);
            if (email.isPresent()) {
                return email;
            }
        }
        for (RDN rdn : subject.getRDNs(BCStyle.CN)) {
            Optional<String> cn = readRdn(rdn);
            if (cn.isPresent() && cn.get().contains("@")) {
                return cn;
            }
        }
        return Optional.empty();
    }

    private Optional<String> readRdn(RDN rdn) {
        ASN1Encodable enc = rdn.getFirst() == null ? null : rdn.getFirst().getValue();
        return enc == null ? Optional.empty() : Optional.of(IETFUtils.valueToString(enc));
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
