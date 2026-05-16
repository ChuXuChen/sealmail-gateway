package com.sealmail.app.usecase.config;

import com.sealmail.app.dto.request.CreateDlpPatternRequest;
import com.sealmail.app.dto.request.CreateDlpSelectionRequest;
import com.sealmail.app.dto.request.UpdateDlpPatternRequest;
import com.sealmail.app.dto.request.UpdateDlpSelectionRequest;
import com.sealmail.app.dto.response.DlpPatternResponse;
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
                request.action(),
                request.severity(),
                request.priority(),
                request.enabled()
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
                settings.action().name(),
                settings.severity(),
                settings.priority(),
                settings.enabled(),
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
