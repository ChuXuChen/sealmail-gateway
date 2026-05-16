package com.sealmail.app.usecase.certificate;

import com.sealmail.app.dto.request.ImportCrlRequest;
import com.sealmail.app.dto.response.CertificateResponse;
import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.exception.CertificateException;
import com.sealmail.app.exception.ResourceNotFoundException;
import com.sealmail.app.mapper.CertificateDtoMapper;
import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.spi.CertificateCryptoPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ImportCrlUseCase {

    private final CertificateRepository certificateRepository;
    private final CertificateCryptoPort certificateCryptoPort;
    private final CertificateDtoMapper mapper;
    private final PermissionChecker permissionChecker;

    @Transactional
    public CertificateResponse execute(String caCertId, ImportCrlRequest request, UserContext user) {
        permissionChecker.checkCanManageCa(user);

        Certificate caCert = certificateRepository.findById(new CertificateId(caCertId))
                .orElseThrow(() -> new ResourceNotFoundException("Certificate", caCertId));
        if (!caCert.isCA()) {
            throw BusinessException.badRequest("证书不是 CA，不能导入 CRL");
        }

        try {
            CertificateCryptoPort.CrlContent crl = certificateCryptoPort.normalizeAndValidateCrl(
                    caCert.getPemContent(),
                    request.getCrlPem(),
                    request.getCrlDerBase64());
            caCert.setImportedCrlPem(crl.pem());
            certificateRepository.save(caCert);
            return mapper.toResponse(caCert);
        } catch (IllegalArgumentException e) {
            throw CertificateException.invalidCertificate(e.getMessage());
        } catch (CertificateException e) {
            throw e;
        } catch (Exception e) {
            throw CertificateException.invalidCertificate("CRL 导入失败: " + e.getMessage());
        }
    }
}
