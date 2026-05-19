package com.sealmail.infra.dlp.config;

import com.sealmail.domain.dlp.config.DlpConfigPort.DlpEdmDatasetSettings;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpEdmDatasetSettingsUpdate;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpFingerprintImport;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpFingerprintLibrarySettings;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpFingerprintLibrarySettingsUpdate;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpImportResult;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpImportValues;

import java.util.List;
import java.util.Set;

public interface DlpDatasetConfigPort {

    List<DlpEdmDatasetSettings> listEdmDatasetSettings();

    DlpEdmDatasetSettings createEdmDataset(DlpEdmDatasetSettingsUpdate update);

    DlpEdmDatasetSettings updateEdmDataset(String id, DlpEdmDatasetSettingsUpdate update);

    DlpImportResult importEdmDatasetValues(String id, DlpImportValues update);

    void deleteEdmDataset(String id);

    List<DlpFingerprintLibrarySettings> listFingerprintLibrarySettings();

    DlpFingerprintLibrarySettings createFingerprintLibrary(DlpFingerprintLibrarySettingsUpdate update);

    DlpFingerprintLibrarySettings updateFingerprintLibrary(String id, DlpFingerprintLibrarySettingsUpdate update);

    DlpImportResult importFingerprintDocument(String id, DlpFingerprintImport update);

    void deleteFingerprintLibrary(String id);

    boolean edmDatasetEnabled(String datasetId);

    Set<String> edmHashes(String datasetId);

    boolean fingerprintLibraryEnabled(String libraryId);

    Set<String> fingerprintHashes(String libraryId);
}
