package com.sealmail.web.controller.v1.mailauth;

import com.sealmail.app.dto.request.DomainMailAuthPolicyRequest;
import com.sealmail.app.dto.request.MailAuthPolicyRequest;
import com.sealmail.app.dto.request.RotateDkimSelectorRequest;
import com.sealmail.app.dto.response.DnsRecordResponse;
import com.sealmail.app.dto.response.DomainMailAuthPolicyResponse;
import com.sealmail.app.dto.response.MailAuthDnsProbeResponse;
import com.sealmail.app.dto.response.MailAuthModernStatusResponse;
import com.sealmail.app.dto.response.MailAuthPolicyResponse;
import com.sealmail.app.security.UserContext;
import com.sealmail.app.usecase.mailauth.GenerateMailAuthDnsRecordsUseCase;
import com.sealmail.app.usecase.mailauth.ProbeMailAuthDnsUseCase;
import com.sealmail.app.usecase.mailauth.QueryDomainMailAuthPolicyUseCase;
import com.sealmail.app.usecase.mailauth.QueryMailAuthPolicyUseCase;
import com.sealmail.app.usecase.mailauth.QueryMailAuthStatusUseCase;
import com.sealmail.app.usecase.mailauth.RotateDkimSelectorUseCase;
import com.sealmail.app.usecase.mailauth.UpdateDomainMailAuthPolicyUseCase;
import com.sealmail.app.usecase.mailauth.UpdateMailAuthPolicyUseCase;
import com.sealmail.web.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mail-auth")
@Tag(name = "邮件认证策略", description = "DKIM、SPF、DMARC现代化策略API")
public class MailAuthPolicyController {

    private final QueryMailAuthPolicyUseCase queryPolicyUseCase;
    private final UpdateMailAuthPolicyUseCase updatePolicyUseCase;
    private final QueryDomainMailAuthPolicyUseCase queryDomainPolicyUseCase;
    private final UpdateDomainMailAuthPolicyUseCase updateDomainPolicyUseCase;
    private final GenerateMailAuthDnsRecordsUseCase generateDnsRecordsUseCase;
    private final ProbeMailAuthDnsUseCase probeDnsUseCase;
    private final RotateDkimSelectorUseCase rotateDkimSelectorUseCase;
    private final QueryMailAuthStatusUseCase queryStatusUseCase;

    public MailAuthPolicyController(QueryMailAuthPolicyUseCase queryPolicyUseCase,
                                    UpdateMailAuthPolicyUseCase updatePolicyUseCase,
                                    QueryDomainMailAuthPolicyUseCase queryDomainPolicyUseCase,
                                    UpdateDomainMailAuthPolicyUseCase updateDomainPolicyUseCase,
                                    GenerateMailAuthDnsRecordsUseCase generateDnsRecordsUseCase,
                                    ProbeMailAuthDnsUseCase probeDnsUseCase,
                                    RotateDkimSelectorUseCase rotateDkimSelectorUseCase,
                                    QueryMailAuthStatusUseCase queryStatusUseCase) {
        this.queryPolicyUseCase = queryPolicyUseCase;
        this.updatePolicyUseCase = updatePolicyUseCase;
        this.queryDomainPolicyUseCase = queryDomainPolicyUseCase;
        this.updateDomainPolicyUseCase = updateDomainPolicyUseCase;
        this.generateDnsRecordsUseCase = generateDnsRecordsUseCase;
        this.probeDnsUseCase = probeDnsUseCase;
        this.rotateDkimSelectorUseCase = rotateDkimSelectorUseCase;
        this.queryStatusUseCase = queryStatusUseCase;
    }

    @GetMapping("/policy")
    @Operation(summary = "查询全局邮件认证策略")
    public ApiResponse<MailAuthPolicyResponse> policy(@AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(queryPolicyUseCase.execute(user));
    }

    @PutMapping("/policy")
    @Operation(summary = "更新全局邮件认证策略")
    public ApiResponse<MailAuthPolicyResponse> updatePolicy(
            @RequestBody MailAuthPolicyRequest request,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(updatePolicyUseCase.execute(request, user));
    }

    @GetMapping("/status")
    @Operation(summary = "查询邮件认证现代化状态")
    public ApiResponse<MailAuthModernStatusResponse> status(@AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(queryStatusUseCase.execute(user));
    }

    @GetMapping("/domains/{domain}/policy")
    @Operation(summary = "查询域名邮件认证策略")
    public ApiResponse<DomainMailAuthPolicyResponse> domainPolicy(
            @PathVariable String domain,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(queryDomainPolicyUseCase.execute(domain, user));
    }

    @PutMapping("/domains/{domain}/policy")
    @Operation(summary = "更新域名邮件认证策略")
    public ApiResponse<DomainMailAuthPolicyResponse> updateDomainPolicy(
            @PathVariable String domain,
            @RequestBody DomainMailAuthPolicyRequest request,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(updateDomainPolicyUseCase.execute(domain, request, user));
    }

    @GetMapping("/domains/{domain}/dns-records")
    @Operation(summary = "生成域名邮件认证DNS记录")
    public ApiResponse<List<DnsRecordResponse>> dnsRecords(
            @PathVariable String domain,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(generateDnsRecordsUseCase.execute(domain, user));
    }

    @PostMapping("/domains/{domain}/dns-probe")
    @Operation(summary = "探测域名邮件认证DNS记录")
    public ApiResponse<List<MailAuthDnsProbeResponse>> dnsProbe(
            @PathVariable String domain,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(probeDnsUseCase.execute(domain, user));
    }

    @PostMapping("/domains/{domain}/dkim/rotate-selector")
    @Operation(summary = "轮换域名DKIM selector")
    public ApiResponse<DomainMailAuthPolicyResponse> rotateDkimSelector(
            @PathVariable String domain,
            @RequestBody RotateDkimSelectorRequest request,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(rotateDkimSelectorUseCase.execute(domain, request, user));
    }

}
