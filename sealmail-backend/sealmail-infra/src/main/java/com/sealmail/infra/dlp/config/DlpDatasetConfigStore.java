package com.sealmail.infra.dlp.config;

import com.sealmail.domain.dlp.config.DlpConfigPort.DlpEdmDatasetSettings;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpEdmDatasetSettingsUpdate;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpFingerprintImport;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpFingerprintLibrarySettings;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpFingerprintLibrarySettingsUpdate;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpImportResult;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpImportValues;
import com.sealmail.infra.dlp.DlpHashSupport;
import com.sealmail.infra.persistence.entity.DlpEdmDatasetEntity;
import com.sealmail.infra.persistence.entity.DlpEdmValueEntity;
import com.sealmail.infra.persistence.entity.DlpFingerprintChunkEntity;
import com.sealmail.infra.persistence.entity.DlpFingerprintLibraryEntity;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
class DlpDatasetConfigStore implements DlpDatasetConfigPort {

    private final EntityManager entityManager;
    private final DlpConfigMapper mapper;

    DlpDatasetConfigStore(EntityManager entityManager, DlpConfigMapper mapper) {
        this.entityManager = entityManager;
        this.mapper = mapper;
    }

    @Override
    public List<DlpEdmDatasetSettings> listEdmDatasetSettings() {
        return entityManager.createQuery(
                        "SELECT d FROM DlpEdmDatasetEntity d ORDER BY d.name ASC",
                        DlpEdmDatasetEntity.class)
                .getResultList()
                .stream()
                .map(mapper::toEdmDatasetSettings)
                .toList();
    }

    @Override
    public DlpEdmDatasetSettings createEdmDataset(DlpEdmDatasetSettingsUpdate update) {
        if (update == null) {
            throw new IllegalArgumentException("EDM dataset request cannot be empty");
        }
        Instant now = Instant.now();
        DlpEdmDatasetEntity entity = new DlpEdmDatasetEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setCreatedAt(now);
        applyEdmDatasetUpdate(entity, update, true);
        entity.setValueCount(0);
        entityManager.persist(entity);
        return mapper.toEdmDatasetSettings(entity);
    }

    @Override
    public DlpEdmDatasetSettings updateEdmDataset(String id, DlpEdmDatasetSettingsUpdate update) {
        if (update == null) {
            throw new IllegalArgumentException("EDM dataset request cannot be empty");
        }
        DlpEdmDatasetEntity entity = requireEdmDataset(id);
        applyEdmDatasetUpdate(entity, update, false);
        entityManager.merge(entity);
        return mapper.toEdmDatasetSettings(entity);
    }

    @Override
    public DlpImportResult importEdmDatasetValues(String id, DlpImportValues update) {
        DlpEdmDatasetEntity dataset = requireEdmDataset(id);
        List<String> values = importValues(update);
        Set<String> existingHashes = edmHashes(id);
        long imported = 0;
        long duplicate = 0;
        long ignored = 0;
        Instant now = Instant.now();
        for (String value : values) {
            String normalized = DlpHashSupport.normalizeExactValue(value);
            if (normalized.length() < 3) {
                ignored++;
                continue;
            }
            String hash = DlpHashSupport.sha256(normalized);
            if (!existingHashes.add(hash)) {
                duplicate++;
                continue;
            }
            DlpEdmValueEntity entity = new DlpEdmValueEntity();
            entity.setId(UUID.randomUUID().toString());
            entity.setDatasetId(id);
            entity.setValueHash(hash);
            entity.setCreatedAt(now);
            entityManager.persist(entity);
            imported++;

            String compactDigits = DlpHashSupport.compactDigits(value);
            if (compactDigits.length() >= 8) {
                String compactHash = DlpHashSupport.sha256(compactDigits);
                if (existingHashes.add(compactHash)) {
                    DlpEdmValueEntity compact = new DlpEdmValueEntity();
                    compact.setId(UUID.randomUUID().toString());
                    compact.setDatasetId(id);
                    compact.setValueHash(compactHash);
                    compact.setCreatedAt(now);
                    entityManager.persist(compact);
                }
            }
        }
        dataset.setValueCount(countEdmValues(id));
        dataset.setUpdatedAt(now);
        entityManager.merge(dataset);
        return new DlpImportResult(imported, duplicate, ignored, values.size());
    }

    @Override
    public void deleteEdmDataset(String id) {
        DlpEdmDatasetEntity entity = requireEdmDataset(id);
        entityManager.remove(entity);
    }

    @Override
    public List<DlpFingerprintLibrarySettings> listFingerprintLibrarySettings() {
        return entityManager.createQuery(
                        "SELECT l FROM DlpFingerprintLibraryEntity l ORDER BY l.name ASC",
                        DlpFingerprintLibraryEntity.class)
                .getResultList()
                .stream()
                .map(mapper::toFingerprintLibrarySettings)
                .toList();
    }

    @Override
    public DlpFingerprintLibrarySettings createFingerprintLibrary(DlpFingerprintLibrarySettingsUpdate update) {
        if (update == null) {
            throw new IllegalArgumentException("Fingerprint library request cannot be empty");
        }
        Instant now = Instant.now();
        DlpFingerprintLibraryEntity entity = new DlpFingerprintLibraryEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setCreatedAt(now);
        applyFingerprintLibraryUpdate(entity, update, true);
        entity.setDocumentCount(0);
        entity.setChunkCount(0);
        entityManager.persist(entity);
        return mapper.toFingerprintLibrarySettings(entity);
    }

    @Override
    public DlpFingerprintLibrarySettings updateFingerprintLibrary(String id, DlpFingerprintLibrarySettingsUpdate update) {
        if (update == null) {
            throw new IllegalArgumentException("Fingerprint library request cannot be empty");
        }
        DlpFingerprintLibraryEntity entity = requireFingerprintLibrary(id);
        applyFingerprintLibraryUpdate(entity, update, false);
        entityManager.merge(entity);
        return mapper.toFingerprintLibrarySettings(entity);
    }

    @Override
    public DlpImportResult importFingerprintDocument(String id, DlpFingerprintImport update) {
        DlpFingerprintLibraryEntity library = requireFingerprintLibrary(id);
        String text = update != null ? update.text() : null;
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Fingerprint document text cannot be blank");
        }
        String documentId = UUID.randomUUID().toString();
        String documentName = update.documentName() == null || update.documentName().isBlank()
                ? "document-" + documentId
                : update.documentName().trim();
        List<String> chunks = fingerprintChunks(text);
        Set<String> unique = new LinkedHashSet<>(chunks);
        Instant now = Instant.now();
        long imported = 0;
        long duplicate = 0;
        Set<String> existing = fingerprintHashes(id);
        for (String hash : unique) {
            if (!existing.add(hash)) {
                duplicate++;
                continue;
            }
            DlpFingerprintChunkEntity entity = new DlpFingerprintChunkEntity();
            entity.setId(UUID.randomUUID().toString());
            entity.setLibraryId(id);
            entity.setDocumentId(documentId);
            entity.setDocumentName(documentName);
            entity.setChunkHash(hash);
            entity.setCreatedAt(now);
            entityManager.persist(entity);
            imported++;
        }
        if (!unique.isEmpty()) {
            library.setDocumentCount(library.getDocumentCount() + 1);
        }
        library.setChunkCount(countFingerprintChunks(id));
        library.setUpdatedAt(now);
        entityManager.merge(library);
        return new DlpImportResult(imported, duplicate, chunks.size() - unique.size(), chunks.size());
    }

    @Override
    public void deleteFingerprintLibrary(String id) {
        DlpFingerprintLibraryEntity entity = requireFingerprintLibrary(id);
        entityManager.remove(entity);
    }

    @Override
    public boolean edmDatasetEnabled(String datasetId) {
        return Optional.ofNullable(entityManager.find(DlpEdmDatasetEntity.class, datasetId))
                .map(DlpEdmDatasetEntity::isEnabled)
                .orElse(false);
    }

    @Override
    public Set<String> edmHashes(String datasetId) {
        if (datasetId == null || datasetId.isBlank()) {
            return Set.of();
        }
        return new HashSet<>(entityManager.createQuery(
                        "SELECT v.valueHash FROM DlpEdmValueEntity v WHERE v.datasetId = :datasetId",
                        String.class)
                .setParameter("datasetId", datasetId)
                .getResultList());
    }

    @Override
    public boolean fingerprintLibraryEnabled(String libraryId) {
        return Optional.ofNullable(entityManager.find(DlpFingerprintLibraryEntity.class, libraryId))
                .map(DlpFingerprintLibraryEntity::isEnabled)
                .orElse(false);
    }

    @Override
    public Set<String> fingerprintHashes(String libraryId) {
        if (libraryId == null || libraryId.isBlank()) {
            return Set.of();
        }
        return new HashSet<>(entityManager.createQuery(
                        "SELECT c.chunkHash FROM DlpFingerprintChunkEntity c WHERE c.libraryId = :libraryId",
                        String.class)
                .setParameter("libraryId", libraryId)
                .getResultList());
    }

    private void applyEdmDatasetUpdate(DlpEdmDatasetEntity entity,
                                       DlpEdmDatasetSettingsUpdate update,
                                       boolean create) {
        if (create || update.name() != null) {
            entity.setName(DlpConfigValidation.requireText(update.name(), "EDM 数据集名称不能为空"));
        }
        if (update.description() != null) {
            entity.setDescription(DlpConfigValidation.blankToNull(update.description()));
        } else if (create) {
            entity.setDescription(null);
        }
        if (create || update.enabled() != null) {
            entity.setEnabled(update.enabled() == null || update.enabled());
        }
        entity.setUpdatedAt(Instant.now());
    }

    private void applyFingerprintLibraryUpdate(DlpFingerprintLibraryEntity entity,
                                               DlpFingerprintLibrarySettingsUpdate update,
                                               boolean create) {
        if (create || update.name() != null) {
            entity.setName(DlpConfigValidation.requireText(update.name(), "文档指纹库名称不能为空"));
        }
        if (update.description() != null) {
            entity.setDescription(DlpConfigValidation.blankToNull(update.description()));
        } else if (create) {
            entity.setDescription(null);
        }
        if (create || update.enabled() != null) {
            entity.setEnabled(update.enabled() == null || update.enabled());
        }
        entity.setUpdatedAt(Instant.now());
    }

    private DlpEdmDatasetEntity requireEdmDataset(String id) {
        DlpEdmDatasetEntity entity = entityManager.find(DlpEdmDatasetEntity.class, id);
        if (entity == null) {
            throw new IllegalArgumentException("EDM dataset not found: " + id);
        }
        return entity;
    }

    private DlpFingerprintLibraryEntity requireFingerprintLibrary(String id) {
        DlpFingerprintLibraryEntity entity = entityManager.find(DlpFingerprintLibraryEntity.class, id);
        if (entity == null) {
            throw new IllegalArgumentException("Fingerprint library not found: " + id);
        }
        return entity;
    }

    private long countEdmValues(String datasetId) {
        return entityManager.createQuery(
                        "SELECT COUNT(v.id) FROM DlpEdmValueEntity v WHERE v.datasetId = :datasetId",
                        Long.class)
                .setParameter("datasetId", datasetId)
                .getSingleResult();
    }

    private long countFingerprintChunks(String libraryId) {
        return entityManager.createQuery(
                        "SELECT COUNT(c.id) FROM DlpFingerprintChunkEntity c WHERE c.libraryId = :libraryId",
                        Long.class)
                .setParameter("libraryId", libraryId)
                .getSingleResult();
    }

    private List<String> importValues(DlpImportValues update) {
        List<String> values = new ArrayList<>();
        if (update != null) {
            values.addAll(update.values());
            if (update.text() != null) {
                for (String line : update.text().split("\\R|,")) {
                    if (!line.isBlank()) {
                        values.add(line.trim());
                    }
                }
            }
        }
        return values;
    }

    private List<String> fingerprintChunks(String text) {
        String normalized = Optional.ofNullable(text)
                .orElse("")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        String[] tokens = normalized.split("\\s+");
        List<String> hashes = new ArrayList<>();
        if (tokens.length >= 5) {
            for (int i = 0; i <= tokens.length - 5; i++) {
                hashes.add(DlpHashSupport.sha256(String.join(" ", java.util.Arrays.copyOfRange(tokens, i, i + 5))));
            }
            return hashes;
        }
        String compact = normalized.replace(" ", "");
        int window = Math.min(32, compact.length());
        if (window < 8) {
            return List.of();
        }
        for (int i = 0; i <= compact.length() - window; i++) {
            hashes.add(DlpHashSupport.sha256(compact.substring(i, i + window)));
        }
        return hashes;
    }
}
