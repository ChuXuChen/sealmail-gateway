package com.sealmail.web.controller.v1;

import com.sealmail.app.security.UserContext;
import com.sealmail.app.dto.response.SmimeSignatureValidationResponse;
import com.sealmail.app.usecase.mail.SmimeOperationUseCase;
import com.sealmail.web.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@Profile("dev")
@RequestMapping("/api/v1/smime")
@RequiredArgsConstructor
@Tag(name = "S/MIME 加密", description = "邮件加密、解密、签名、验证操作")
public class SMIMEController {

    private final SmimeOperationUseCase smimeOperationUseCase;

    @Data
    public static class EncryptRequest {
        @NotBlank(message = "邮件内容不能为空")
        private String content;
        @NotBlank(message = "收件人证书不能为空")
        private String recipientCert;
    }

    @Data
    public static class EncryptMultipleRequest {
        @NotBlank(message = "邮件内容不能为空")
        private String content;
        private List<String> recipientCerts;
    }

    @Data
    public static class DecryptRequest {
        @NotBlank(message = "加密内容不能为空")
        private String encryptedContent;
        @NotBlank(message = "私钥不能为空")
        private String privateKey;
        private String certificate;
    }

    @Data
    public static class SignRequest {
        @NotBlank(message = "邮件内容不能为空")
        private String content;
        @NotBlank(message = "私钥不能为空")
        private String privateKey;
        @NotBlank(message = "证书不能为空")
        private String certificate;
    }

    @Data
    public static class VerifyRequest {
        @NotBlank(message = "签名内容不能为空")
        private String signedContent;
        @NotBlank(message = "发件人证书不能为空")
        private String senderCert;
    }

    @PostMapping("/encrypt")
    @Operation(summary = "加密邮件", description = "使用收件人证书加密邮件内容")
    public ApiResponse<String> encrypt(
            @Valid @RequestBody EncryptRequest request,
            @AuthenticationPrincipal UserContext user) {

        return ApiResponse.ok(smimeOperationUseCase.encrypt(request.getContent(), request.getRecipientCert()));
    }

    @PostMapping("/encrypt-multiple")
    @Operation(summary = "加密邮件（多收件人）", description = "使用多个收件人证书加密邮件")
    public ApiResponse<String> encryptMultiple(
            @Valid @RequestBody EncryptMultipleRequest request,
            @AuthenticationPrincipal UserContext user) {

        return ApiResponse.ok(smimeOperationUseCase.encryptMultiple(request.getContent(), request.getRecipientCerts()));
    }

    @PostMapping("/decrypt")
    @Operation(summary = "解密邮件", description = "使用私钥解密邮件")
    public ApiResponse<String> decrypt(
            @Valid @RequestBody DecryptRequest request,
            @AuthenticationPrincipal UserContext user) {

        return ApiResponse.ok(smimeOperationUseCase.decrypt(
                request.getEncryptedContent(),
                request.getPrivateKey(),
                request.getCertificate()));
    }

    @PostMapping("/sign")
    @Operation(summary = "签名邮件", description = "使用私钥和证书对邮件签名")
    public ApiResponse<String> sign(
            @Valid @RequestBody SignRequest request,
            @AuthenticationPrincipal UserContext user) {

        return ApiResponse.ok(smimeOperationUseCase.sign(
                request.getContent(),
                request.getPrivateKey(),
                request.getCertificate()));
    }

    @PostMapping("/verify")
    @Operation(summary = "验证签名", description = "验证邮件签名的有效性")
    public ApiResponse<SmimeSignatureValidationResponse> verify(
            @Valid @RequestBody VerifyRequest request,
            @AuthenticationPrincipal UserContext user) {

        return ApiResponse.ok(smimeOperationUseCase.verify(request.getSignedContent(), request.getSenderCert()));
    }

    @PostMapping("/extract-content")
    @Operation(summary = "提取签名邮件内容", description = "从签名邮件中提取原始内容")
    public ApiResponse<String> extractSignedContent(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserContext user) {

        String signedContent = body.get("signedContent");
        return ApiResponse.ok(smimeOperationUseCase.extractSignedContent(signedContent));
    }

    @PostMapping("/check-encrypted")
    @Operation(summary = "检测是否加密", description = "检测邮件是否为加密格式")
    public ApiResponse<Boolean> checkEncrypted(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserContext user) {

        String content = body.get("content");
        return ApiResponse.ok(smimeOperationUseCase.isEncrypted(content));
    }

    @PostMapping("/check-signed")
    @Operation(summary = "检测是否签名", description = "检测邮件是否为签名格式")
    public ApiResponse<Boolean> checkSigned(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserContext user) {

        String content = body.get("content");
        return ApiResponse.ok(smimeOperationUseCase.isSigned(content));
    }
}
