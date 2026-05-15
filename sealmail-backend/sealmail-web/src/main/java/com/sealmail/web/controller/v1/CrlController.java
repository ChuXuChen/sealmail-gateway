package com.sealmail.web.controller.v1;

import com.sealmail.app.usecase.certificate.GenerateCrlUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.cert.X509CRL;

/**
 * Anonymous CRL distribution endpoint. Embedded clients fetch this URL from the
 * CRL Distribution Point extension of issued certs.
 *
 * <ul>
 *   <li>{@code GET /api/v1/crl/{caCertId}} — DER (application/pkix-crl)</li>
 *   <li>{@code GET /api/v1/crl/{caCertId}.pem} — PEM (text/plain)</li>
 * </ul>
 *
 * Public by design (whitelisted in SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/crl")
@RequiredArgsConstructor
@Tag(name = "CRL 分发", description = "匿名获取 CA 的吊销列表，供外部验证器使用")
public class CrlController {

    private static final MediaType PKIX_CRL = MediaType.parseMediaType("application/pkix-crl");

    private final GenerateCrlUseCase generateCrlUseCase;

    @GetMapping("/{caCertId}")
    @Operation(summary = "下载 CRL (DER)")
    public ResponseEntity<byte[]> getCrlDer(
            @Parameter(description = "CA 证书指纹") @PathVariable String caCertId) throws Exception {
        X509CRL crl = generateCrlUseCase.execute(caCertId);
        byte[] der = crl.getEncoded();
        return ResponseEntity.ok()
                .contentType(PKIX_CRL)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + caCertId + ".crl\"")
                .body(der);
    }

    @GetMapping("/{caCertId}.pem")
    @Operation(summary = "下载 CRL (PEM)")
    public ResponseEntity<String> getCrlPem(
            @Parameter(description = "CA 证书指纹") @PathVariable String caCertId) {
        X509CRL crl = generateCrlUseCase.execute(caCertId);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(generateCrlUseCase.toPem(crl));
    }
}
