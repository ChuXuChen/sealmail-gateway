package com.sealmail.infra.dlp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sealmail.domain.dlp.DlpPolicy;
import com.sealmail.domain.dlp.DlpRule;
import com.sealmail.domain.dlp.DlpRuleGroup;
import com.sealmail.domain.dlp.config.DlpConfigPort;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpEdmDatasetSettings;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpEdmDatasetSettingsUpdate;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpFingerprintImport;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpFingerprintLibrarySettings;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpFingerprintLibrarySettingsUpdate;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpImportResult;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpImportValues;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpPatternSettings;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpPatternSettingsUpdate;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpPolicySettings;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpPolicySettingsUpdate;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpRuleGroupSettings;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpRuleGroupSettingsUpdate;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpRuleSettings;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpRuleSettingsUpdate;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpSelectionSettings;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpSelectionSettingsUpdate;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.infra.events.DomainEventPublisher;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Primary
@Service
@Transactional
public class DlpConfigService implements DlpConfigPort,
        DlpPatternConfigPort,
        DlpSelectionConfigPort,
        DlpPolicyConfigPort,
        DlpDatasetConfigPort,
        DlpRuntimeConfigPort {

    private final DlpPatternConfigStore patternStore;
    private final DlpSelectionConfigStore selectionStore;
    private final DlpRuleGroupPolicyStore policyStore;
    private final DlpDatasetConfigStore datasetStore;
    private final DlpConfigMapper mapper;

    @Autowired
    DlpConfigService(DlpPatternConfigStore patternStore,
                     DlpSelectionConfigStore selectionStore,
                     DlpRuleGroupPolicyStore policyStore,
                     DlpDatasetConfigStore datasetStore,
                     DlpConfigMapper mapper) {
        this.patternStore = patternStore;
        this.selectionStore = selectionStore;
        this.policyStore = policyStore;
        this.datasetStore = datasetStore;
        this.mapper = mapper;
    }

    public DlpConfigService(EntityManager entityManager,
                            DomainEventPublisher domainEventPublisher,
                            ObjectMapper objectMapper) {
        this.mapper = new DlpConfigMapper(objectMapper);
        DlpConfigEvents events = new DlpConfigEvents(domainEventPublisher);
        this.patternStore = new DlpPatternConfigStore(entityManager, mapper, events);
        this.selectionStore = new DlpSelectionConfigStore(entityManager, mapper, events, patternStore);
        this.policyStore = new DlpRuleGroupPolicyStore(entityManager, mapper, patternStore);
        this.datasetStore = new DlpDatasetConfigStore(entityManager, mapper);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlpPatternConfig> listPatterns() {
        return patternStore.listPatterns();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlpPatternSettings> listPatternSettings() {
        return listPatterns().stream()
                .map(mapper::toPatternSettings)
                .toList();
    }

    @Override
    public DlpPatternSettings createPattern(DlpPatternSettingsUpdate update) {
        return mapper.toPatternSettings(createPattern(mapper.toPatternUpdate(update)));
    }

    @Override
    public DlpPatternSettings updatePattern(String id, DlpPatternSettingsUpdate update) {
        return mapper.toPatternSettings(updatePattern(id, mapper.toPatternUpdate(update)));
    }

    @Override
    public DlpPatternConfig createPattern(DlpPatternUpdate update) {
        return patternStore.createPattern(update);
    }

    @Override
    public DlpPatternConfig updatePattern(String id, DlpPatternUpdate update) {
        return patternStore.updatePattern(id, update);
    }

    @Override
    public void deletePattern(String id) {
        patternStore.deletePattern(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlpRuleSettings> listRuleSettings() {
        return listRules().stream()
                .map(mapper::toRuleSettings)
                .toList();
    }

    @Override
    public DlpRuleSettings createRule(DlpRuleSettingsUpdate update) {
        return mapper.toRuleSettings(createPattern(mapper.toPatternUpdate(update)));
    }

    @Override
    public DlpRuleSettings updateRule(String id, DlpRuleSettingsUpdate update) {
        return mapper.toRuleSettings(updatePattern(id, mapper.toPatternUpdate(update)));
    }

    @Override
    public void deleteRule(String id) {
        deletePattern(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlpRule> listRules() {
        return patternStore.listRules();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlpRule> activeRules() {
        return patternStore.activeRules();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlpPatternConfig> activePatterns() {
        return patternStore.activePatterns();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlpPatternConfig> activePatternsFor(MailEnvelope envelope) {
        return selectionStore.activePatternsFor(envelope);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlpSelectionConfig> listSelections() {
        return selectionStore.listSelections();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlpSelectionSettings> listSelectionSettings() {
        return listSelections().stream()
                .map(mapper::toSelectionSettings)
                .toList();
    }

    @Override
    public DlpSelectionSettings createSelection(DlpSelectionSettingsUpdate update) {
        return mapper.toSelectionSettings(createSelection(mapper.toSelectionUpdate(update)));
    }

    @Override
    public DlpSelectionSettings updateSelection(String id, DlpSelectionSettingsUpdate update) {
        return mapper.toSelectionSettings(updateSelection(id, mapper.toSelectionUpdate(update)));
    }

    @Override
    public DlpSelectionConfig createSelection(DlpSelectionUpdate update) {
        return selectionStore.createSelection(update);
    }

    @Override
    public DlpSelectionConfig updateSelection(String id, DlpSelectionUpdate update) {
        return selectionStore.updateSelection(id, update);
    }

    @Override
    public void deleteSelection(String id) {
        selectionStore.deleteSelection(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlpSelectionConfig> matchingSelections(MailEnvelope envelope) {
        return selectionStore.matchingSelections(envelope);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean appliesTo(MailEnvelope envelope) {
        return selectionStore.appliesTo(envelope);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlpRuleGroupSettings> listRuleGroupSettings() {
        return listRuleGroups().stream()
                .map(mapper::toRuleGroupSettings)
                .toList();
    }

    @Override
    public DlpRuleGroupSettings createRuleGroup(DlpRuleGroupSettingsUpdate update) {
        return mapper.toRuleGroupSettings(createRuleGroupConfig(update));
    }

    @Override
    public DlpRuleGroupSettings updateRuleGroup(String id, DlpRuleGroupSettingsUpdate update) {
        return mapper.toRuleGroupSettings(updateRuleGroupConfig(id, update));
    }

    @Override
    public void deleteRuleGroup(String id) {
        policyStore.deleteRuleGroup(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlpRuleGroup> listRuleGroups() {
        return policyStore.listRuleGroups();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlpRuleGroup> activeRuleGroups() {
        return policyStore.activeRuleGroups();
    }

    @Override
    public DlpRuleGroup createRuleGroupConfig(DlpRuleGroupSettingsUpdate update) {
        return policyStore.createRuleGroupConfig(update);
    }

    @Override
    public DlpRuleGroup updateRuleGroupConfig(String id, DlpRuleGroupSettingsUpdate update) {
        return policyStore.updateRuleGroupConfig(id, update);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlpPolicySettings> listPolicySettings() {
        return listPolicies().stream()
                .map(mapper::toPolicySettings)
                .toList();
    }

    @Override
    public DlpPolicySettings createPolicy(DlpPolicySettingsUpdate update) {
        return mapper.toPolicySettings(createPolicyConfig(update));
    }

    @Override
    public DlpPolicySettings updatePolicy(String id, DlpPolicySettingsUpdate update) {
        return mapper.toPolicySettings(updatePolicyConfig(id, update));
    }

    @Override
    public void deletePolicy(String id) {
        policyStore.deletePolicy(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlpPolicy> listPolicies() {
        return policyStore.listPolicies();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlpPolicy> activePolicies() {
        return policyStore.activePolicies();
    }

    @Override
    public DlpPolicy createPolicyConfig(DlpPolicySettingsUpdate update) {
        return policyStore.createPolicyConfig(update);
    }

    @Override
    public DlpPolicy updatePolicyConfig(String id, DlpPolicySettingsUpdate update) {
        return policyStore.updatePolicyConfig(id, update);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlpEdmDatasetSettings> listEdmDatasetSettings() {
        return datasetStore.listEdmDatasetSettings();
    }

    @Override
    public DlpEdmDatasetSettings createEdmDataset(DlpEdmDatasetSettingsUpdate update) {
        return datasetStore.createEdmDataset(update);
    }

    @Override
    public DlpEdmDatasetSettings updateEdmDataset(String id, DlpEdmDatasetSettingsUpdate update) {
        return datasetStore.updateEdmDataset(id, update);
    }

    @Override
    public DlpImportResult importEdmDatasetValues(String id, DlpImportValues update) {
        return datasetStore.importEdmDatasetValues(id, update);
    }

    @Override
    public void deleteEdmDataset(String id) {
        datasetStore.deleteEdmDataset(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlpFingerprintLibrarySettings> listFingerprintLibrarySettings() {
        return datasetStore.listFingerprintLibrarySettings();
    }

    @Override
    public DlpFingerprintLibrarySettings createFingerprintLibrary(DlpFingerprintLibrarySettingsUpdate update) {
        return datasetStore.createFingerprintLibrary(update);
    }

    @Override
    public DlpFingerprintLibrarySettings updateFingerprintLibrary(String id, DlpFingerprintLibrarySettingsUpdate update) {
        return datasetStore.updateFingerprintLibrary(id, update);
    }

    @Override
    public DlpImportResult importFingerprintDocument(String id, DlpFingerprintImport update) {
        return datasetStore.importFingerprintDocument(id, update);
    }

    @Override
    public void deleteFingerprintLibrary(String id) {
        datasetStore.deleteFingerprintLibrary(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean edmDatasetEnabled(String datasetId) {
        return datasetStore.edmDatasetEnabled(datasetId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> edmHashes(String datasetId) {
        return datasetStore.edmHashes(datasetId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean fingerprintLibraryEnabled(String libraryId) {
        return datasetStore.fingerprintLibraryEnabled(libraryId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> fingerprintHashes(String libraryId) {
        return datasetStore.fingerprintHashes(libraryId);
    }
}
