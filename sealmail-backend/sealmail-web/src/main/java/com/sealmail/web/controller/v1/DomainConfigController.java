package com.sealmail.web.controller.v1;

import com.sealmail.app.dto.request.CreateDomainConfigRequest;
import com.sealmail.app.dto.request.UpdateDomainConfigRequest;
import com.sealmail.app.dto.response.DomainConfigResponse;
import com.sealmail.app.security.UserContext;
import com.sealmail.app.usecase.domain.CreateDomainConfigUseCase;
import com.sealmail.app.usecase.domain.DeleteDomainConfigUseCase;
import com.sealmail.app.usecase.domain.QueryDomainConfigUseCase;
import com.sealmail.app.usecase.domain.UpdateDomainConfigUseCase;
import com.sealmail.web.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/domains")
@RequiredArgsConstructor
@Tag(name = "域名配置", description = "邮件域名的加密、签名等配置管理")
public class DomainConfigController {

    private final QueryDomainConfigUseCase queryUseCase;
    private final CreateDomainConfigUseCase createUseCase;
    private final UpdateDomainConfigUseCase updateUseCase;
    private final DeleteDomainConfigUseCase deleteUseCase;

    @GetMapping
    @Operation(summary = "查询所有域名配置", description = "获取所有域名配置列表")
    public ApiResponse<List<DomainConfigResponse>> findAll(
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(queryUseCase.findAll(user));
    }

    @GetMapping("/active")
    @Operation(summary = "查询所有启用的域名配置", description = "获取所有启用的域名配置")
    public ApiResponse<List<DomainConfigResponse>> findAllActive(
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(queryUseCase.findAllActive(user));
    }

    @GetMapping("/local")
    @Operation(summary = "查询本地域名配置", description = "获取所有本地域名配置")
    public ApiResponse<List<DomainConfigResponse>> findLocalDomains(
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(queryUseCase.findLocalDomains(user));
    }

    @GetMapping("/remote")
    @Operation(summary = "查询远程域名配置", description = "获取所有远程域名配置")
    public ApiResponse<List<DomainConfigResponse>> findRemoteDomains(
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(queryUseCase.findRemoteDomains(user));
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询域名配置", description = "获取指定ID的域名配置")
    public ApiResponse<DomainConfigResponse> findById(
            @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(queryUseCase.findById(id, user));
    }

    @GetMapping("/domain/{domain}")
    @Operation(summary = "根据域名查询配置", description = "获取指定域名的配置")
    public ApiResponse<DomainConfigResponse> findByDomain(
            @PathVariable String domain,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(queryUseCase.findByDomain(domain, user));
    }

    @PostMapping
    @Operation(summary = "创建域名配置", description = "创建新的域名配置")
    public ApiResponse<DomainConfigResponse> create(
            @Valid @RequestBody CreateDomainConfigRequest request,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(createUseCase.execute(request, user));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新域名配置", description = "更新指定ID的域名配置")
    public ApiResponse<DomainConfigResponse> update(
            @PathVariable String id,
            @RequestBody UpdateDomainConfigRequest request,
            @AuthenticationPrincipal UserContext user) {
        return ApiResponse.ok(updateUseCase.execute(id, request, user));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除域名配置", description = "删除指定ID的域名配置")
    public ApiResponse<Void> delete(
            @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        deleteUseCase.execute(id, user);
        return ApiResponse.ok(null);
    }
}
