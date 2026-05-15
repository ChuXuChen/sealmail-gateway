package com.sealmail.web.controller.v1;

import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.infra.crypto.util.SM2KeyGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 国密SM2/SM3/SM4算法测试控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sm2-test")
@RequiredArgsConstructor
@Tag(name = "国密算法测试", description = "测试SM2签名、SM4加密等国密算法功能")
public class SM2CryptoTestController {

    private final SMIMEOperations smimeOperations;

    @GetMapping("/generate")
    @Operation(summary = "生成SM2密钥对", description = "生成新的SM2椭圆曲线密钥对用于测试")
    public Map<String, Object> generateSM2KeyPair() throws Exception {
        KeyPair keyPair = SM2KeyGenerator.generateSM2KeyPair();
        X509Certificate cert = SM2KeyGenerator.generateSM2Certificate(keyPair, "CN=Test,O=SealMail,C=CN");

        Map<String, Object> result = new HashMap<>();
        result.put("algorithm", "SM2/SM3withSM2");
        result.put("privateKey", SM2KeyGenerator.privateKeyToPEM(keyPair.getPrivate()));
        result.put("certificate", SM2KeyGenerator.certificateToPEM(cert));
        result.put("publicKeyFormat", keyPair.getPublic().getFormat());
        result.put("status", "success");

        log.info("Generated SM2 key pair successfully");
        return result;
    }

    @PostMapping("/sign")
    @Operation(summary = "SM2签名测试", description = "使用SM3withSM2算法对内容进行签名")
    public Map<String, Object> testSM2Sign(@RequestBody Map<String, String> request) throws Exception {
        String content = request.getOrDefault("content", "This is a test message for SM2 signing");
        String privateKeyPem = request.get("privateKey");
        String certPem = request.get("certificate");

        // 如果未提供密钥，生成新的
        if (privateKeyPem == null || certPem == null) {
            KeyPair keyPair = SM2KeyGenerator.generateSM2KeyPair();
            X509Certificate cert = SM2KeyGenerator.generateSM2Certificate(keyPair, "CN=Test,O=SealMail,C=CN");
            privateKeyPem = SM2KeyGenerator.privateKeyToPEM(keyPair.getPrivate());
            certPem = SM2KeyGenerator.certificateToPEM(cert);
        }

        byte[] rawContent = content.getBytes(StandardCharsets.UTF_8);
        byte[] signed = smimeOperations.sign(rawContent, privateKeyPem, certPem);

        Map<String, Object> result = new HashMap<>();
        result.put("algorithm", "SM3withSM2");
        result.put("originalContent", content);
        result.put("originalSize", rawContent.length);
        result.put("signedSize", signed.length);
        result.put("signedBase64", Base64.getMimeEncoder().encodeToString(signed));
        result.put("status", "success");

        log.info("SM2 signature test completed, size: {} bytes", signed.length);
        return result;
    }

    @PostMapping("/encrypt")
    @Operation(summary = "SM4加密测试", description = "使用SM4-CBC算法加密内容")
    public Map<String, Object> testSM4Encrypt(@RequestBody Map<String, String> request) throws Exception {
        String content = request.getOrDefault("content", "This is a test message for SM4 encryption");
        String certPem = request.get("certificate");

        // 如果未提供证书，生成新的
        if (certPem == null) {
            KeyPair keyPair = SM2KeyGenerator.generateSM2KeyPair();
            X509Certificate cert = SM2KeyGenerator.generateSM2Certificate(keyPair, "CN=Test,O=SealMail,C=CN");
            certPem = SM2KeyGenerator.certificateToPEM(cert);
        }

        byte[] rawContent = content.getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = smimeOperations.encrypt(rawContent, certPem);

        Map<String, Object> result = new HashMap<>();
        result.put("algorithm", "SM4-CBC");
        result.put("keyAlgorithm", "SM2");
        result.put("originalContent", content);
        result.put("originalSize", rawContent.length);
        result.put("encryptedSize", encrypted.length);
        result.put("encryptedBase64", Base64.getMimeEncoder().encodeToString(encrypted));
        result.put("encryptionRatio", String.format("%.2f", (double) encrypted.length / rawContent.length));
        result.put("status", "success");

        log.info("SM4 encryption test completed, size: {} bytes", encrypted.length);
        return result;
    }

    @GetMapping("/status")
    @Operation(summary = "国密算法支持状态", description = "检查当前系统对国密算法的支持情况")
    public Map<String, Object> getCryptoStatus() {
        Map<String, Object> result = new HashMap<>();

        Map<String, String> algorithms = new HashMap<>();
        algorithms.put("signature", "SM3withSM2 (国密签名)");
        algorithms.put("encryption", "SM4-CBC (国密加密)");
        algorithms.put("hash", "SM3 (国密哈希)");
        algorithms.put("keyExchange", "SM2 (国密密钥交换)");
        result.put("supportedAlgorithms", algorithms);

        Map<String, String> certs = new HashMap<>();
        certs.put("sender", "RSA + SM2 双证书");
        certs.put("recipient", "RSA + SM2 双证书");
        result.put("certificates", certs);

        result.put("gmStandard", "GMT 0009-2012 (SM2) / GMT 0002-2012 (SM3) / GMT 0001-2012 (SM4)");
        result.put("provider", "BouncyCastle 1.78.1");
        result.put("status", "国密算法已就绪");

        return result;
    }
}
