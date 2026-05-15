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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ImportCrlUseCase {

    private final CertificateRepository certificateRepository;
    private final CertificateCryptoService cryptoService;
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
            X509Certificate caX509 = cryptoService.parseCertificate(caCert.getPemContent());
            X509CRL crl = parseRequestCrl(request);

            if (!crl.getIssuerX500Principal().equals(caX509.getSubjectX500Principal())) {
                throw CertificateException.invalidCertificate("CRL issuer 与所选 CA subject 不一致");
            }
            crl.verify(caX509.getPublicKey(), "BC");

            caCert.setImportedCrlPem(cryptoService.toPem(crl));
            certificateRepository.save(caCert);
            return mapper.toResponse(caCert);
        } catch (CertificateException e) {
            throw e;
        } catch (Exception e) {
            throw CertificateException.invalidCertificate("CRL 导入失败: " + e.getMessage());
        }
    }

    private X509CRL parseRequestCrl(ImportCrlRequest request) throws Exception {
        if (request.getCrlPem() != null && !request.getCrlPem().isBlank()) {
            return cryptoService.parseCrl(request.getCrlPem());
        }
        if (request.getCrlDerBase64() != null && !request.getCrlDerBase64().isBlank()) {
            return cryptoService.parseCrl(Base64.getDecoder().decode(request.getCrlDerBase64()));
        }
        throw CertificateException.invalidCertificate("请提供 PEM CRL 或 DER .crl 文件内容");
    }
}
