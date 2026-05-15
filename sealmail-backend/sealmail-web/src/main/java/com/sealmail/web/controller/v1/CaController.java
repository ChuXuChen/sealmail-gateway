package com.sealmail.web.controller.v1;

import com.sealmail.app.dto.request.CreateIntermediateCaRequest;
import com.sealmail.app.dto.request.CreateRootCaRequest;
import com.sealmail.app.dto.request.ImportCertificateRequest;
import com.sealmail.app.dto.request.ImportCrlRequest;
import com.sealmail.app.dto.response.CertificateResponse;
import com.sealmail.app.security.UserContext;
import com.sealmail.app.usecase.certificate.CreateIntermediateCaUseCase;
import com.sealmail.app.usecase.certificate.CreateRootCaUseCase;
import com.sealmail.app.usecase.certificate.DeleteCertificateUseCase;
import com.sealmail.app.usecase.certificate.ImportCertificateUseCase;
import com.sealmail.app.usecase.certificate.ImportCrlUseCase;
import com.sealmail.app.usecase.certificate.QueryCertificateUseCase;
import com.sealmail.app.usecase.certificate.RevokeCertificateUseCase;
import com.sealmail.app.usecase.certificate.TrustCertificateUseCase;
import com.sealmail.web.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * CA management. Distinct from {@code /api/v1/certificates} which only deals
 * with end-entity certificates.
 */
@RestController
@RequestMapping("/api/v1/cas")
@RequiredArgsConstructor
@Tag(name = "CA 管理", description = "Root CA / Intermediate CA 创建、导入、查询、吊销")
public class CaController {

    private final QueryCertificateUseCase queryUseCase;
    private final CreateRootCaUseCase createRootCaUseCase;
    private final CreateIntermediateCaUseCase createIntermediateCaUseCase;
    private final ImportCertificateUseCase importCertificateUseCase;
    private final ImportCrlUseCase importCrlUseCase;
    private final RevokeCertificateUseCase revokeUseCase;
    private final TrustCertificateUseCase trustUseCase;
    private final DeleteCertificateUseCase deleteUseCase;

    @GetMapping
    @Operation(summary = "列出所有 CA（Root + Intermediate）")
    public ApiResponse<List<CertificateResponse>> listAll(@AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(queryUseCase.findAllCAs(user));
    }

    @PostMapping("/root")
    @Operation(summary = "创建 Root CA", description = "自签名，BasicConstraints CA=true, pathLen=1")
    public ApiResponse<CertificateResponse> createRoot(
            @Valid @RequestBody CreateRootCaRequest body,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(createRootCaUseCase.execute(body, user));
    }

    @PostMapping("/intermediate")
    @Operation(summary = "创建 Intermediate CA",
            description = "由 Root CA 签发，BasicConstraints CA=true, pathLen=0")
    public ApiResponse<CertificateResponse> createIntermediate(
            @Valid @RequestBody CreateIntermediateCaRequest body,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(createIntermediateCaUseCase.execute(body, user));
    }

    @PostMapping("/import")
    @Operation(summary = "导入外部 CA 证书", description = "PEM 证书 + 私钥 (PEM)；导入后默认 isCA 由证书 BasicConstraints 决定")
    public ApiResponse<CertificateResponse> importCa(
            @Valid @RequestBody ImportCertificateRequest body,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(importCertificateUseCase.execute(body, user));
    }

    @PostMapping("/{id}/crl")
    @Operation(summary = "导入外部 CA 的完整 CRL",
            description = "保存外部 CA 已发布的 CRL；之后匿名 CRL 分发端点会优先返回该完整 CRL")
    public ApiResponse<CertificateResponse> importCrl(
            @Parameter(description = "CA 证书指纹") @PathVariable String id,
            @Valid @RequestBody ImportCrlRequest body,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(importCrlUseCase.execute(id, body, user));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询单个 CA")
    public ApiResponse<CertificateResponse> findById(
            @Parameter(description = "CA 证书指纹") @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(queryUseCase.findById(id, user));
    }

    @PostMapping("/{id}/trust")
    @Operation(summary = "信任 CA", description = "幂等")
    public ApiResponse<CertificateResponse> trust(
            @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(trustUseCase.execute(id, user));
    }

    @PostMapping("/{id}/untrust")
    @Operation(summary = "撤销 CA 信任", description = "幂等")
    public ApiResponse<CertificateResponse> untrust(
            @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(trustUseCase.untrust(id, user));
    }

    @PostMapping("/{id}/revoke")
    @Operation(summary = "吊销 CA", description = "吊销后该 CA 不能再签发；级联吊销所有它签发的证书")
    public ApiResponse<CertificateResponse> revoke(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal UserContext user) {
        String reason = body != null ? body.get("reason") : "CA revoked by administrator";
        return ApiResponse.ok(revokeUseCase.execute(id, reason, user));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除 CA")
    public ApiResponse<Void> delete(
            @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        deleteUseCase.execute(id, user);
        return ApiResponse.noContent();
    }
}
