package com.sealmail.app.usecase.certificate;

import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.spi.CertificateCryptoPort;
import com.sealmail.domain.shared.model.EmailAddress;
import org.springframework.stereotype.Component;

@Component
class CertificateMaterialAssembler {

    Certificate issued(CertificateCryptoPort.CertificateDescriptor descriptor, EmailAddress owner) {
        Certificate cert = Certificate.issueCertificate(
                new CertificateId(descriptor.thumbprint()),
                owner,
                descriptor.pemContent(),
                descriptor.validity(),
                descriptor.keyUsages(),
                descriptor.issuerDn(),
                descriptor.subjectDn(),
                descriptor.serialNumber(),
                descriptor.subjectKeyIdentifier());
        applyMetadata(cert, descriptor);
        return cert;
    }

    Certificate imported(CertificateCryptoPort.CertificateDescriptor descriptor, EmailAddress owner) {
        Certificate cert = Certificate.importCertificate(
                new CertificateId(descriptor.thumbprint()),
                owner,
                descriptor.pemContent(),
                descriptor.validity(),
                descriptor.keyUsages(),
                descriptor.issuerDn(),
                descriptor.subjectDn(),
                descriptor.serialNumber(),
                descriptor.subjectKeyIdentifier());
        applyMetadata(cert, descriptor);
        return cert;
    }

    private void applyMetadata(Certificate cert, CertificateCryptoPort.CertificateDescriptor descriptor) {
        cert.setAlgorithm(descriptor.algorithm());
        if (descriptor.ca()) {
            cert.markAsCA(descriptor.pathLenConstraint() == null ? 0 : descriptor.pathLenConstraint());
        }
        cert.setExtendedKeyUsages(descriptor.extendedKeyUsages());
        cert.setCrlDistributionPointUrl(descriptor.crlDistributionPointUrl());
    }
}
