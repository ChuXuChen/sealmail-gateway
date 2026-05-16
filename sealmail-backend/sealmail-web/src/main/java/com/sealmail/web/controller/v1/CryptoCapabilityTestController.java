package com.sealmail.web.controller.v1;

import com.sealmail.app.dto.response.CryptoKeyMaterialResponse;
import com.sealmail.app.usecase.mail.SmimeOperationUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@Profile("dev")
@RequestMapping("/api/v1/crypto-test")
@RequiredArgsConstructor
@Tag(name = "密码能力测试", description = "测试当前密码能力的密钥材料、签名、加密和支持状态")
public class CryptoCapabilityTestController {

    private final SmimeOperationUseCase smimeOperationUseCase;

    @GetMapping("/generate")
    @Operation(summary = "生成测试密钥材料", description = "生成用于本地测试的临时密钥材料")
    public CryptoKeyMaterialResponse generateKeyMaterial() {
        CryptoKeyMaterialResponse result = smimeOperationUseCase.generateTestKeyMaterial();
        log.info("Generated test key material successfully");
        return result;
    }

    @PostMapping("/sign")
    @Operation(summary = "签名能力测试", description = "对内容进行签名")
    public Map<String, Object> testSign(@RequestBody Map<String, String> request) throws Exception {
        String content = request.getOrDefault("content", "This is a test message for signing");
        String privateKeyPem = request.get("privateKey");
        String certPem = request.get("certificate");

        SmimeOperationUseCase.SignedPayload payload =
                smimeOperationUseCase.signForTest(content, privateKeyPem, certPem);

        log.info("Signature capability test completed");
        return Map.of(
                "originalContent", payload.originalContent(),
                "signedBase64", payload.signedBase64(),
                "status", payload.status());
    }

    @PostMapping("/encrypt")
    @Operation(summary = "加密能力测试", description = "对内容进行加密")
    public Map<String, Object> testEncrypt(@RequestBody Map<String, String> request) {
        String content = request.getOrDefault("content", "This is a test message for encryption");
        String certPem = request.get("certificate");

        SmimeOperationUseCase.EncryptedPayload payload =
                smimeOperationUseCase.encryptForTest(content, certPem);

        log.info("Encryption capability test completed");
        return Map.of(
                "originalContent", payload.originalContent(),
                "encryptedBase64", payload.encryptedBase64(),
                "status", payload.status());
    }

    @GetMapping("/status")
    @Operation(summary = "密码能力支持状态", description = "检查当前系统对密码能力的支持情况")
    public SmimeOperationUseCase.CryptoStatus getCryptoStatus() {
        return smimeOperationUseCase.cryptoStatus();
    }
}
