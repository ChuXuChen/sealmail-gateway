package com.sealmail.app.usecase.config;

import com.sealmail.app.dto.request.CreateDlpPatternRequest;
import com.sealmail.app.dto.request.CreateDlpSelectionRequest;
import com.sealmail.app.dto.request.DlpPolicyRequest;
import com.sealmail.app.dto.request.DlpRuleGroupRequest;
import com.sealmail.app.dto.request.DlpRuleRequest;
import com.sealmail.app.dto.request.UpdateDlpPatternRequest;
import com.sealmail.app.dto.request.UpdateDlpSelectionRequest;
import com.sealmail.app.dto.response.DlpPatternResponse;
import com.sealmail.app.dto.response.DlpPolicyResponse;
import com.sealmail.app.dto.response.DlpRuleGroupResponse;
import com.sealmail.app.dto.response.DlpRuleResponse;
import com.sealmail.app.dto.response.DlpSelectionResponse;
import com.sealmail.app.exception.SecurityException;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.dlp.config.DlpConfigPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ManageDlpConfigUseCase {

    private final DlpConfigPort dlpConfigPort;

    public ManageDlpConfigUseCase(DlpConfigPort dlpConfigPort) {
        this.dlpConfigPort = dlpConfigPort;
    }

    @Transactional(readOnly = true)
    public List<DlpPatternResponse> listPatterns(UserContext user) {
        requireAdmin(user);
        return dlpConfigPort.listPatternSettings().stream()
                .map(this::toPatternResponse)
                .toList();
    }

    @Transactional
    public DlpPatternResponse createPattern(CreateDlpPatternRequest request, UserContext user) {
        requireAdmin(user);
        return toPatternResponse(dlpConfigPort.createPattern(toPatternUpdate(request)));
    }

    @Transactional
    public DlpPatternResponse updatePattern(String id, UpdateDlpPatternRequest request, UserContext user) {
        requireAdmin(user);
        return toPatternResponse(dlpConfigPort.updatePattern(id, toPatternUpdate(request)));
    }

    @Transactional
    public void deletePattern(String id, UserContext user) {
        requireAdmin(user);
        dlpConfigPort.deletePattern(id);
    }

    @Transactional(readOnly = true)
    public List<DlpSelectionResponse> listSelections(UserContext user) {
        requireAdmin(user);
        return dlpConfigPort.listSelectionSettings().stream()
                .map(this::toSelectionResponse)
                .toList();
    }

    @Transactional
    public DlpSelectionResponse createSelection(CreateDlpSelectionRequest request, UserContext user) {
        requireAdmin(user);
        return toSelectionResponse(dlpConfigPort.createSelection(toSelectionUpdate(request)));
    }

    @Transactional
    public DlpSelectionResponse updateSelection(String id, UpdateDlpSelectionRequest request, UserContext user) {
        requireAdmin(user);
        return toSelectionResponse(dlpConfigPort.updateSelection(id, toSelectionUpdate(request)));
    }

    @Transactional
    public void deleteSelection(String id, UserContext user) {
        requireAdmin(user);
        dlpConfigPort.deleteSelection(id);
    }

    @Transactional(readOnly = true)
    public List<DlpRuleResponse> listRules(UserContext user) {
        requireAdmin(user);
        return dlpConfigPort.listRuleSettings().stream()
                .map(this::toRuleResponse)
                .toList();
    }

    @Transactional
    public DlpRuleResponse createRule(DlpRuleRequest request, UserContext user) {
        requireAdmin(user);
        return toRuleResponse(dlpConfigPort.createRule(toRuleUpdate(request)));
    }

    @Transactional
    public DlpRuleResponse updateRule(String id, DlpRuleRequest request, UserContext user) {
        requireAdmin(user);
        return toRuleResponse(dlpConfigPort.updateRule(id, toRuleUpdate(request)));
    }

    @Transactional
    public void deleteRule(String id, UserContext user) {
        requireAdmin(user);
        dlpConfigPort.deleteRule(id);
    }

    @Transactional(readOnly = true)
    public List<DlpRuleGroupResponse> listRuleGroups(UserContext user) {
        requireAdmin(user);
        return dlpConfigPort.listRuleGroupSettings().stream()
                .map(this::toRuleGroupResponse)
                .toList();
    }

    @Transactional
    public DlpRuleGroupResponse createRuleGroup(DlpRuleGroupRequest request, UserContext user) {
        requireAdmin(user);
        return toRuleGroupResponse(dlpConfigPort.createRuleGroup(toRuleGroupUpdate(request)));
    }

    @Transactional
    public DlpRuleGroupResponse updateRuleGroup(String id, DlpRuleGroupRequest request, UserContext user) {
        requireAdmin(user);
        return toRuleGroupResponse(dlpConfigPort.updateRuleGroup(id, toRuleGroupUpdate(request)));
    }

    @Transactional
    public void deleteRuleGroup(String id, UserContext user) {
        requireAdmin(user);
        dlpConfigPort.deleteRuleGroup(id);
    }

    @Transactional(readOnly = true)
    public List<DlpPolicyResponse> listPolicies(UserContext user) {
        requireAdmin(user);
        return dlpConfigPort.listPolicySettings().stream()
                .map(this::toPolicyResponse)
                .toList();
    }

    @Transactional
    public DlpPolicyResponse createPolicy(DlpPolicyRequest request, UserContext user) {
        requireAdmin(user);
        return toPolicyResponse(dlpConfigPort.createPolicy(toPolicyUpdate(request)));
    }

    @Transactional
    public DlpPolicyResponse updatePolicy(String id, DlpPolicyRequest request, UserContext user) {
        requireAdmin(user);
        return toPolicyResponse(dlpConfigPort.updatePolicy(id, toPolicyUpdate(request)));
    }

    @Transactional
    public void deletePolicy(String id, UserContext user) {
        requireAdmin(user);
        dlpConfigPort.deletePolicy(id);
    }

    private void requireAdmin(UserContext user) {
        if (user == null || !user.isAdmin()) {
            throw SecurityException.accessDenied("Only administrators can manage DLP");
        }
    }

    private DlpConfigPort.DlpPatternSettingsUpdate toPatternUpdate(CreateDlpPatternRequest request) {
        return new DlpConfigPort.DlpPatternSettingsUpdate(
                request.name(),
                request.description(),
                request.regex(),
                request.type(),
                request.builtinCode(),
                request.contentKinds(),
                request.minMatchCount(),
                request.maxEvidenceCount(),
                request.maskingStrategy(),
                request.action(),
                request.severity(),
                request.priority(),
                request.enabled()
        );
    }

    private DlpConfigPort.DlpPatternSettingsUpdate toPatternUpdate(UpdateDlpPatternRequest request) {
        return new DlpConfigPort.DlpPatternSettingsUpdate(
                request.name(),
                request.description(),
                request.regex(),
                request.type(),
                request.builtinCode(),
                request.contentKinds(),
                request.minMatchCount(),
                request.maxEvidenceCount(),
                request.maskingStrategy(),
                request.action(),
                request.severity(),
                request.priority(),
                request.enabled()
        );
    }

    private DlpConfigPort.DlpRuleSettingsUpdate toRuleUpdate(DlpRuleRequest request) {
        return new DlpConfigPort.DlpRuleSettingsUpdate(
                request.name(),
                request.description(),
                request.type(),
                request.pattern(),
                request.builtinCode(),
                request.contentKinds(),
                request.minMatchCount(),
                request.maxEvidenceCount(),
                request.maskingStrategy(),
                request.action(),
                request.severity(),
                request.priority(),
                request.enabled()
        );
    }

    private DlpConfigPort.DlpRuleGroupSettingsUpdate toRuleGroupUpdate(DlpRuleGroupRequest request) {
        return new DlpConfigPort.DlpRuleGroupSettingsUpdate(
                request.name(),
                request.description(),
                request.enabled(),
                request.priority(),
                request.ruleIds()
        );
    }

    private DlpConfigPort.DlpPolicySettingsUpdate toPolicyUpdate(DlpPolicyRequest request) {
        return new DlpConfigPort.DlpPolicySettingsUpdate(
                request.name(),
                request.description(),
                request.mode(),
                request.direction(),
                request.senderDomains(),
                request.recipientDomains(),
                request.senderAddressPatterns(),
                request.recipientAddressPatterns(),
                request.attachmentRequired(),
                request.enabled(),
                request.priority(),
                request.ruleGroupIds()
        );
    }

    private DlpConfigPort.DlpSelectionSettingsUpdate toSelectionUpdate(CreateDlpSelectionRequest request) {
        return new DlpConfigPort.DlpSelectionSettingsUpdate(
                request.scopeType(),
                request.scopeValue(),
                request.patternIds(),
                request.patternMode(),
                request.enabled()
        );
    }

    private DlpConfigPort.DlpSelectionSettingsUpdate toSelectionUpdate(UpdateDlpSelectionRequest request) {
        return new DlpConfigPort.DlpSelectionSettingsUpdate(
                request.scopeType(),
                request.scopeValue(),
                request.patternIds(),
                request.patternMode(),
                request.enabled()
        );
    }

    private DlpPatternResponse toPatternResponse(DlpConfigPort.DlpPatternSettings settings) {
        return new DlpPatternResponse(
                settings.id(),
                settings.name(),
                settings.description(),
                settings.regex(),
                settings.type().name(),
                settings.builtinCode(),
                settings.contentKinds().stream().map(Enum::name).toList(),
                settings.minMatchCount(),
                settings.maxEvidenceCount(),
                settings.maskingStrategy().name(),
                settings.action().name(),
                settings.severity(),
                settings.priority(),
                settings.enabled(),
                settings.createdAt(),
                settings.updatedAt()
        );
    }

    private DlpRuleResponse toRuleResponse(DlpConfigPort.DlpRuleSettings settings) {
        return new DlpRuleResponse(
                settings.id(),
                settings.name(),
                settings.description(),
                settings.type().name(),
                settings.pattern(),
                settings.builtinCode(),
                settings.contentKinds().stream().map(Enum::name).toList(),
                settings.minMatchCount(),
                settings.maxEvidenceCount(),
                settings.maskingStrategy().name(),
                settings.action().name(),
                settings.severity(),
                settings.priority(),
                settings.enabled(),
                settings.createdAt(),
                settings.updatedAt()
        );
    }

    private DlpRuleGroupResponse toRuleGroupResponse(DlpConfigPort.DlpRuleGroupSettings settings) {
        return new DlpRuleGroupResponse(
                settings.id(),
                settings.name(),
                settings.description(),
                settings.enabled(),
                settings.priority(),
                settings.ruleIds(),
                settings.createdAt(),
                settings.updatedAt()
        );
    }

    private DlpPolicyResponse toPolicyResponse(DlpConfigPort.DlpPolicySettings settings) {
        return new DlpPolicyResponse(
                settings.id(),
                settings.name(),
                settings.description(),
                settings.mode().name(),
                settings.direction() != null ? settings.direction().name() : null,
                settings.senderDomains(),
                settings.recipientDomains(),
                settings.senderAddressPatterns(),
                settings.recipientAddressPatterns(),
                settings.attachmentRequired(),
                settings.enabled(),
                settings.priority(),
                settings.ruleGroupIds(),
                settings.createdAt(),
                settings.updatedAt()
        );
    }

    private DlpSelectionResponse toSelectionResponse(DlpConfigPort.DlpSelectionSettings settings) {
        return new DlpSelectionResponse(
                settings.id(),
                settings.scopeType().name(),
                settings.scopeValue(),
                settings.patternIds(),
                settings.enabled(),
                settings.createdAt(),
                settings.updatedAt()
        );
    }
}
