package com.sealmail.web.controller.v1;

import com.sealmail.app.dto.request.CertificateBindingRequest;
import com.sealmail.app.dto.response.CertificateBindingResponse;
import com.sealmail.app.security.UserContext;
import com.sealmail.app.usecase.certificate.ManageCertificateBindingUseCase;
import com.sealmail.web.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/certificate-bindings")
@RequiredArgsConstructor
@Tag(name = "证书绑定", description = "显式管理邮箱身份与证书用途绑定")
public class CertificateBindingController {

    private final ManageCertificateBindingUseCase useCase;

    @GetMapping
    @Operation(summary = "查询证书绑定")
    public ApiResponse<List<CertificateBindingResponse>> list(
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String owner,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(useCase.list(domain, owner, user));
    }

    @PostMapping
    @Operation(summary = "创建或更新证书绑定")
    public ApiResponse<CertificateBindingResponse> upsert(
            @Valid @RequestBody CertificateBindingRequest request,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(useCase.upsert(request, user));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除证书绑定")
    public ApiResponse<Void> delete(
            @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        useCase.delete(id, user);
        return ApiResponse.noContent();
    }
}
