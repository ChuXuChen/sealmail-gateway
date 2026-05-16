package com.sealmail.app.usecase.certificate;

import com.sealmail.app.dto.request.CertificateBindingRequest;
import com.sealmail.app.dto.response.CertificateBindingResponse;
import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.exception.ResourceNotFoundException;
import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateBinding;
import com.sealmail.domain.certificate.CertificateBindingPurpose;
import com.sealmail.domain.certificate.CertificateBindingRepository;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.KeyUsage;
import com.sealmail.domain.shared.model.EmailAddress;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ManageCertificateBindingUseCase {

    private final CertificateBindingRepository bindingRepository;
    private final CertificateRepository certificateRepository;
    private final PermissionChecker permissionChecker;
    private final CertificateChainService certificateChainService;

    public ManageCertificateBindingUseCase(CertificateBindingRepository bindingRepository,
                                           CertificateRepository certificateRepository,
                                           PermissionChecker permissionChecker,
                                           CertificateChainService certificateChainService) {
        this.bindingRepository = bindingRepository;
        this.certificateRepository = certificateRepository;
        this.permissionChecker = permissionChecker;
        this.certificateChainService = certificateChainService;
    }

    @Transactional(readOnly = true)
    public List<CertificateBindingResponse> list(String domain, String ownerEmail, UserContext user) {
        permissionChecker.checkAuthenticated(user);
        List<CertificateBinding> bindings;
        if (ownerEmail != null && !ownerEmail.isBlank()) {
            EmailAddress owner = new EmailAddress(ownerEmail);
            permissionChecker.checkCanViewCertificates(user, owner.getDomain());
            bindings = bindingRepository.findByOwner(owner);
        } else if (domain != null && !domain.isBlank()) {
            permissionChecker.checkCanViewCertificates(user, domain);
            bindings = bindingRepository.findByDomain(domain);
        } else {
            permissionChecker.checkCanViewAllCertificates(user);
            bindings = bindingRepository.findAll();
        }
        return bindings.stream().map(this::toResponse).toList();
    }

    @Transactional
    public CertificateBindingResponse upsert(CertificateBindingRequest request, UserContext user) {
        EmailAddress owner = new EmailAddress(request.ownerEmail());
        CertificateBindingPurpose purpose = parsePurpose(request.purpose());
        permissionChecker.checkCanManageCertificates(user, owner.getDomain());

        Certificate certificate = certificateRepository.findById(new CertificateId(request.certificateId()))
                .orElseThrow(() -> new ResourceNotFoundException("Certificate", request.certificateId()));
        validateCertificate(owner, purpose, certificate);

        java.util.Optional<CertificateBinding> existingBinding = bindingRepository.findByOwnerAndPurpose(owner, purpose);
        CertificateBinding binding = existingBinding
                .orElseGet(() -> CertificateBinding.create(
                        UUID.randomUUID().toString(),
                        owner,
                        certificate.getId(),
                        purpose,
                        request.enabled()));
        if (existingBinding.isPresent()) {
            binding.rebind(certificate.getId(), request.enabled());
        }
        return toResponse(bindingRepository.save(binding));
    }

    @Transactional
    public void delete(String id, UserContext user) {
        CertificateBinding binding = bindingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CertificateBinding", id));
        permissionChecker.checkCanManageCertificates(user, binding.getDomain());
        bindingRepository.deleteById(id);
    }

    private void validateCertificate(EmailAddress owner,
                                     CertificateBindingPurpose purpose,
                                     Certificate certificate) {
        if (!certificate.getOwner().equals(owner)) {
            throw BusinessException.badRequest("证书所有者与绑定邮箱不一致");
        }
        if (!certificate.isTrusted() || certificate.isRevoked() || !certificateChainService.isChainTrustedAndUsable(certificate)) {
            throw BusinessException.badRequest("证书未信任、已吊销或证书链不可用");
        }
        if (purpose == CertificateBindingPurpose.ENCRYPTION
                && !certificate.getKeyUsages().contains(KeyUsage.ENCRYPTION)) {
            throw BusinessException.badRequest("证书不支持加密用途");
        }
        if (purpose == CertificateBindingPurpose.SIGNING
                && !certificate.getKeyUsages().contains(KeyUsage.SIGNING)) {
            throw BusinessException.badRequest("证书不支持签名用途");
        }
        if (purpose == CertificateBindingPurpose.SIGNING && !certificate.hasPrivateKey()) {
            throw BusinessException.badRequest("签名绑定要求证书具有私钥");
        }
    }

    private CertificateBindingPurpose parsePurpose(String purpose) {
        try {
            return CertificateBindingPurpose.valueOf(purpose);
        } catch (Exception e) {
            throw BusinessException.badRequest("不支持的证书绑定用途: " + purpose);
        }
    }

    private CertificateBindingResponse toResponse(CertificateBinding binding) {
        Certificate certificate = certificateRepository.findById(binding.getCertificateId()).orElse(null);
        return new CertificateBindingResponse(
                binding.getId(),
                binding.getDomain(),
                binding.getOwner().getValue(),
                binding.getCertificateId().getThumbprint(),
                certificate != null ? certificate.getAlias() : null,
                binding.getPurpose().name(),
                binding.isEnabled(),
                binding.getCreatedAt(),
                binding.getUpdatedAt());
    }
}
