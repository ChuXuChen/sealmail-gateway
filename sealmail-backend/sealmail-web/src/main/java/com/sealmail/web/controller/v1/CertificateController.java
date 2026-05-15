package com.sealmail.web.controller.v1;

import com.sealmail.app.dto.common.PageRequest;
import com.sealmail.app.dto.common.PageResponse;
import com.sealmail.app.dto.request.GenerateCertificateRequest;
import com.sealmail.app.dto.request.ImportCertificateRequest;
import com.sealmail.app.dto.request.IssueEndEntityRequest;
import com.sealmail.app.dto.request.SignCsrRequest;
import com.sealmail.app.dto.response.CertificateResponse;
import com.sealmail.app.security.UserContext;
import com.sealmail.app.usecase.certificate.DeleteCertificateUseCase;
import com.sealmail.app.usecase.certificate.GenerateSelfSignedCertificateUseCase;
import com.sealmail.app.usecase.certificate.ImportCertificateUseCase;
import com.sealmail.app.usecase.certificate.IssueEndEntityUseCase;
import com.sealmail.app.usecase.certificate.QueryCertificateUseCase;
import com.sealmail.app.usecase.certificate.RevokeCertificateUseCase;
import com.sealmail.app.usecase.certificate.SignCsrUseCase;
import com.sealmail.app.usecase.certificate.TrustCertificateUseCase;
import com.sealmail.web.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * End-entity (S/MIME user) certificate management. CA management lives at
 * {@code /api/v1/cas}; CRL distribution at {@code /api/v1/crl}.
 */
@RestController
@RequestMapping("/api/v1/certificates")
@RequiredArgsConstructor
@Tag(name = "证书管理（终端）", description = "终端证书查询、导入、签发、信任、吊销")
public class CertificateController {

    private final QueryCertificateUseCase queryCertificateUseCase;
    private final ImportCertificateUseCase importCertificateUseCase;
    private final GenerateSelfSignedCertificateUseCase generateSelfSignedCertificateUseCase;
    private final IssueEndEntityUseCase issueEndEntityUseCase;
    private final SignCsrUseCase signCsrUseCase;
    private final RevokeCertificateUseCase revokeCertificateUseCase;
    private final TrustCertificateUseCase trustCertificateUseCase;
    private final DeleteCertificateUseCase deleteCertificateUseCase;

    @GetMapping("/{id}")
    @Operation(summary = "查询单个证书")
    public ApiResponse<CertificateResponse> findById(
            @Parameter(description = "证书指纹ID") @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(queryCertificateUseCase.findById(id, user));
    }

    @GetMapping
    @Operation(summary = "终端证书列表",
            description = "默认只返回终端证书；传 owner 可按 owner 过滤；传 includeAll=true 返回全部（含 CA）")
    public ApiResponse<PageResponse<CertificateResponse>> list(
            @Parameter(description = "所有者邮箱（可选）")
            @RequestParam(required = false) @Email String owner,
            @Parameter(description = "包括 CA 证书")
            @RequestParam(defaultValue = "false") boolean includeAll,
            @Parameter(description = "页码（从1开始）")
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页大小")
            @RequestParam(defaultValue = "20") @Min(1) @Max(500) int size,
            @AuthenticationPrincipal UserContext user) {

        PageRequest pageRequest = PageRequest.builder().page(page).size(size).build();
        PageResponse<CertificateResponse> result;
        if (owner != null && !owner.isBlank()) {
            result = queryCertificateUseCase.findByOwner(owner, pageRequest, user);
        } else if (includeAll) {
            result = queryCertificateUseCase.findAll(pageRequest, user);
        } else {
            result = queryCertificateUseCase.findAllEndEntities(pageRequest, user);
        }
        return ApiResponse.ok(result);
    }

    @PostMapping("/import")
    @Operation(summary = "导入证书（PEM）",
            description = "可附带私钥；CA 字段由 BasicConstraints 扩展决定")
    public ApiResponse<CertificateResponse> importCertificate(
            @Valid @RequestBody ImportCertificateRequest request,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(importCertificateUseCase.execute(request, user));
    }

    @PostMapping("/generate-self-signed")
    @Operation(summary = "生成自签名终端证书", description = "不挂在 CA 链下；用于快速测试")
    public ApiResponse<CertificateResponse> generateSelfSigned(
            @Valid @RequestBody GenerateCertificateRequest request,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(generateSelfSignedCertificateUseCase.execute(request, user));
    }

    @PostMapping("/issue")
    @Operation(summary = "通过 Intermediate CA 签发终端证书",
            description = "服务端生成密钥对，使用所选 Intermediate CA 签名；EKU=emailProtection；CRL DP 自动填充")
    public ApiResponse<CertificateResponse> issue(
            @Valid @RequestBody IssueEndEntityRequest request,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(issueEndEntityUseCase.execute(request, user));
    }

    @PostMapping("/sign-csr")
    @Operation(summary = "导入 CSR 由 Intermediate CA 签发",
            description = "私钥由请求方持有；服务端只签发证书并保存（不存私钥）")
    public ApiResponse<CertificateResponse> signCsr(
            @Valid @RequestBody SignCsrRequest request,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(signCsrUseCase.execute(request, user));
    }

    @PostMapping("/{id}/trust")
    @Operation(summary = "信任证书", description = "幂等：已信任则原样返回")
    public ApiResponse<CertificateResponse> trust(
            @Parameter(description = "证书指纹ID") @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(trustCertificateUseCase.execute(id, user));
    }

    @PostMapping("/{id}/untrust")
    @Operation(summary = "撤销信任", description = "幂等：本就未信任则原样返回")
    public ApiResponse<CertificateResponse> untrust(
            @Parameter(description = "证书指纹ID") @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(trustCertificateUseCase.untrust(id, user));
    }

    @PostMapping("/{id}/revoke")
    @Operation(summary = "吊销证书", description = "可选 reason 字段；当前不会写入 PKIX CRL reasonCode")
    public ApiResponse<CertificateResponse> revoke(
            @Parameter(description = "证书指纹ID") @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal UserContext user) {
        String reason = body != null ? body.get("reason") : "Manually revoked";
        return ApiResponse.ok(revokeCertificateUseCase.execute(id, reason, user));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除证书")
    public ApiResponse<Void> delete(
            @Parameter(description = "证书指纹ID") @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        deleteCertificateUseCase.execute(id, user);
        return ApiResponse.noContent();
    }
}
