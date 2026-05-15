package com.sealmail.web.controller.v1;

import com.sealmail.app.security.UserContext;
import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.certificate.spi.SignatureValidationResult;
import com.sealmail.web.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/smime")
@RequiredArgsConstructor
@Tag(name = "S/MIME 加密", description = "邮件加密、解密、签名、验证操作")
public class SMIMEController {

    private final SMIMEOperations smimeOperations;

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

        byte[] content = request.getContent().getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = smimeOperations.encrypt(content, request.getRecipientCert());
        String base64 = Base64.getMimeEncoder().encodeToString(encrypted);
        return ApiResponse.ok(base64);
    }

    @PostMapping("/encrypt-multiple")
    @Operation(summary = "加密邮件（多收件人）", description = "使用多个收件人证书加密邮件")
    public ApiResponse<String> encryptMultiple(
            @Valid @RequestBody EncryptMultipleRequest request,
            @AuthenticationPrincipal UserContext user) {

        byte[] content = request.getContent().getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = smimeOperations.encryptMultiple(content, request.getRecipientCerts());
        String base64 = Base64.getMimeEncoder().encodeToString(encrypted);
        return ApiResponse.ok(base64);
    }

    @PostMapping("/decrypt")
    @Operation(summary = "解密邮件", description = "使用私钥解密邮件")
    public ApiResponse<String> decrypt(
            @Valid @RequestBody DecryptRequest request,
            @AuthenticationPrincipal UserContext user) {

        byte[] encrypted = Base64.getMimeDecoder().decode(request.getEncryptedContent());
        byte[] decrypted = smimeOperations.decrypt(encrypted, request.getPrivateKey(), request.getCertificate());
        String content = new String(decrypted, StandardCharsets.UTF_8);
        return ApiResponse.ok(content);
    }

    @PostMapping("/sign")
    @Operation(summary = "签名邮件", description = "使用私钥和证书对邮件签名")
    public ApiResponse<String> sign(
            @Valid @RequestBody SignRequest request,
            @AuthenticationPrincipal UserContext user) {

        byte[] content = request.getContent().getBytes(StandardCharsets.UTF_8);
        byte[] signed = smimeOperations.sign(content, request.getPrivateKey(), request.getCertificate());
        String base64 = Base64.getMimeEncoder().encodeToString(signed);
        return ApiResponse.ok(base64);
    }

    @PostMapping("/verify")
    @Operation(summary = "验证签名", description = "验证邮件签名的有效性")
    public ApiResponse<SignatureValidationResult> verify(
            @Valid @RequestBody VerifyRequest request,
            @AuthenticationPrincipal UserContext user) {

        byte[] signed = Base64.getMimeDecoder().decode(request.getSignedContent());
        SignatureValidationResult result = smimeOperations.verifySignatureDetail(signed, request.getSenderCert());
        return ApiResponse.ok(result);
    }

    @PostMapping("/extract-content")
    @Operation(summary = "提取签名邮件内容", description = "从签名邮件中提取原始内容")
    public ApiResponse<String> extractSignedContent(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserContext user) {

        String signedContent = body.get("signedContent");
        byte[] signed = Base64.getMimeDecoder().decode(signedContent);
        byte[] extracted = smimeOperations.extractSignedContent(signed);
        String content = new String(extracted, StandardCharsets.UTF_8);
        return ApiResponse.ok(content);
    }

    @PostMapping("/check-encrypted")
    @Operation(summary = "检测是否加密", description = "检测邮件是否为加密格式")
    public ApiResponse<Boolean> checkEncrypted(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserContext user) {

        String content = body.get("content");
        byte[] bytes = Base64.getMimeDecoder().decode(content);
        boolean result = smimeOperations.isEncrypted(bytes);
        return ApiResponse.ok(result);
    }

    @PostMapping("/check-signed")
    @Operation(summary = "检测是否签名", description = "检测邮件是否为签名格式")
    public ApiResponse<Boolean> checkSigned(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserContext user) {

        String content = body.get("content");
        byte[] bytes = Base64.getMimeDecoder().decode(content);
        boolean result = smimeOperations.isSigned(bytes);
        return ApiResponse.ok(result);
    }
}
