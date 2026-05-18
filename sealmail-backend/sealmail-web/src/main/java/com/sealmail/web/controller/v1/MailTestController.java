package com.sealmail.web.controller.v1;

import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.security.UserContext;
import com.sealmail.app.usecase.mail.MailTestUseCase;
import com.sealmail.web.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mail-test")
@RequiredArgsConstructor
@Tag(name = "邮件发送测试", description = "测试邮件发送功能")
public class MailTestController {

    private final MailTestUseCase mailTestUseCase;

    @Data
    public static class SendMailWebRequest {
        @NotBlank(message = "发件人不能为空")
        private String from;

        @NotEmpty(message = "收件人不能为空")
        private List<String> to;

        @NotBlank(message = "主题不能为空")
        private String subject;

        @NotBlank(message = "邮件内容不能为空")
        private String content;

        public com.sealmail.app.dto.request.SendMailRequest toAppRequest() {
            return new com.sealmail.app.dto.request.SendMailRequest(from, to, subject, content);
        }
    }

    @PostMapping("/send")
    @Operation(summary = "按当前配置发送测试邮件", description = "不强制 S/MIME；签名、加密与投递路由由当前域名配置决定")
    public ApiResponse<String> sendTestMail(@Valid @RequestBody SendMailWebRequest request,
                                            @AuthenticationPrincipal UserContext user) {
        requireAdmin(user);
        return ApiResponse.ok(mailTestUseCase.sendConfigured(request.toAppRequest(), user));
    }

    @PostMapping("/send-encrypted")
    @Operation(summary = "发送受保护测试邮件", description = "高级入口：强制 S/MIME 签名和加密")
    public ApiResponse<String> sendEncryptedMail(@Valid @RequestBody SendMailWebRequest request,
                                                 @AuthenticationPrincipal UserContext user) {
        requireAdmin(user);
        return ApiResponse.ok(mailTestUseCase.sendProtected(request.toAppRequest(), user));
    }

    @PostMapping("/probe-route")
    @Operation(summary = "探测当前投递路由", description = "使用 host + DeliveryTransportProfile 探测当前收件人域投递路由")
    public ApiResponse<String> probeCurrentRoute(@Valid @RequestBody SendMailWebRequest request,
                                                 @AuthenticationPrincipal UserContext user) {
        requireAdmin(user);
        return ApiResponse.ok(mailTestUseCase.probeCurrentRoute(request.toAppRequest(), user));
    }

    @GetMapping("/test-smtp-config")
    @Operation(summary = "测试SMTP配置", description = "测试SMTP配置连接")
    public ApiResponse<String> testSmtpConfig(@AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(mailTestUseCase.testSmtpConfig(user));
    }

    private void requireAdmin(UserContext user) {
        if (user == null || !user.isAdmin()) {
            throw BusinessException.forbidden("只有管理员可以使用邮件测试工具");
        }
    }

}
