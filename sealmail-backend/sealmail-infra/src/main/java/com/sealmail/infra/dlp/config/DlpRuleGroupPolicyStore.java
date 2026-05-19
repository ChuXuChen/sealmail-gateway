package com.sealmail.infra.dlp.config;

import com.sealmail.domain.dlp.DlpPolicy;
import com.sealmail.domain.dlp.DlpPolicyMode;
import com.sealmail.domain.dlp.DlpRuleGroup;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpPolicySettingsUpdate;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpRuleGroupSettingsUpdate;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.infra.persistence.entity.DlpPolicyEntity;
import com.sealmail.infra.persistence.entity.DlpPolicyRuleGroupEntity;
import com.sealmail.infra.persistence.entity.DlpRuleGroupEntity;
import com.sealmail.infra.persistence.entity.DlpRuleGroupItemEntity;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
class DlpRuleGroupPolicyStore implements DlpPolicyConfigPort {

    private final EntityManager entityManager;
    private final DlpConfigMapper mapper;
    private final DlpPatternConfigStore patternStore;

    DlpRuleGroupPolicyStore(EntityManager entityManager,
                            DlpConfigMapper mapper,
                            DlpPatternConfigStore patternStore) {
        this.entityManager = entityManager;
        this.mapper = mapper;
        this.patternStore = patternStore;
    }

    @Override
    public List<DlpRuleGroup> listRuleGroups() {
        return entityManager.createQuery(
                        "SELECT g FROM DlpRuleGroupEntity g ORDER BY g.priority ASC, g.name ASC",
                        DlpRuleGroupEntity.class)
                .getResultList()
                .stream()
                .map(entity -> mapper.toRuleGroup(entity, ruleIdsForGroup(entity.getId())))
                .toList();
    }

    @Override
    public List<DlpRuleGroup> activeRuleGroups() {
        return listRuleGroups().stream()
                .filter(DlpRuleGroup::enabled)
                .toList();
    }

    @Override
    public DlpRuleGroup createRuleGroupConfig(DlpRuleGroupSettingsUpdate update) {
        if (update == null) {
            throw new IllegalArgumentException("DLP rule group request cannot be empty");
        }
        DlpRuleGroupEntity entity = new DlpRuleGroupEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setCreatedAt(Instant.now());
        applyRuleGroupUpdate(entity, update, true);
        entityManager.persist(entity);
        replaceRuleGroupItems(entity.getId(), DlpConfigValidation.normalizeIds(update.ruleIds()));
        return mapper.toRuleGroup(entity, ruleIdsForGroup(entity.getId()));
    }

    @Override
    public DlpRuleGroup updateRuleGroupConfig(String id, DlpRuleGroupSettingsUpdate update) {
        if (update == null) {
            throw new IllegalArgumentException("DLP rule group request cannot be empty");
        }
        DlpRuleGroupEntity entity = requireRuleGroup(id);
        applyRuleGroupUpdate(entity, update, false);
        entityManager.merge(entity);
        if (update.ruleIds() != null) {
            replaceRuleGroupItems(entity.getId(), DlpConfigValidation.normalizeIds(update.ruleIds()));
        }
        return mapper.toRuleGroup(entity, ruleIdsForGroup(entity.getId()));
    }

    @Override
    public void deleteRuleGroup(String id) {
        DlpRuleGroupEntity entity = requireRuleGroup(id);
        removeRuleGroupItems(id);
        entityManager.remove(entity);
    }

    @Override
    public List<DlpPolicy> listPolicies() {
        return entityManager.createQuery(
                        "SELECT p FROM DlpPolicyEntity p ORDER BY p.priority ASC, p.name ASC",
                        DlpPolicyEntity.class)
                .getResultList()
                .stream()
                .map(entity -> mapper.toPolicy(entity, ruleGroupIdsForPolicy(entity.getId())))
                .toList();
    }

    @Override
    public List<DlpPolicy> activePolicies() {
        return listPolicies().stream()
                .filter(DlpPolicy::enabled)
                .toList();
    }

    @Override
    public DlpPolicy createPolicyConfig(DlpPolicySettingsUpdate update) {
        if (update == null) {
            throw new IllegalArgumentException("DLP policy request cannot be empty");
        }
        DlpPolicyEntity entity = new DlpPolicyEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setCreatedAt(Instant.now());
        applyPolicyUpdate(entity, update, true);
        entityManager.persist(entity);
        replacePolicyRuleGroups(entity.getId(), DlpConfigValidation.normalizeIds(update.ruleGroupIds()));
        return mapper.toPolicy(entity, ruleGroupIdsForPolicy(entity.getId()));
    }

    @Override
    public DlpPolicy updatePolicyConfig(String id, DlpPolicySettingsUpdate update) {
        if (update == null) {
            throw new IllegalArgumentException("DLP policy request cannot be empty");
        }
        DlpPolicyEntity entity = requirePolicy(id);
        applyPolicyUpdate(entity, update, false);
        entityManager.merge(entity);
        if (update.ruleGroupIds() != null) {
            replacePolicyRuleGroups(entity.getId(), DlpConfigValidation.normalizeIds(update.ruleGroupIds()));
        }
        return mapper.toPolicy(entity, ruleGroupIdsForPolicy(entity.getId()));
    }

    @Override
    public void deletePolicy(String id) {
        DlpPolicyEntity entity = requirePolicy(id);
        removePolicyRuleGroups(id);
        entityManager.remove(entity);
    }

    private DlpRuleGroupEntity requireRuleGroup(String id) {
        DlpRuleGroupEntity entity = entityManager.find(DlpRuleGroupEntity.class, id);
        if (entity == null) {
            throw new IllegalArgumentException("DLP rule group not found: " + id);
        }
        return entity;
    }

    private DlpPolicyEntity requirePolicy(String id) {
        DlpPolicyEntity entity = entityManager.find(DlpPolicyEntity.class, id);
        if (entity == null) {
            throw new IllegalArgumentException("DLP policy not found: " + id);
        }
        return entity;
    }

    private void applyRuleGroupUpdate(DlpRuleGroupEntity entity,
                                      DlpRuleGroupSettingsUpdate update,
                                      boolean create) {
        if (create || update.name() != null) {
            entity.setName(DlpConfigValidation.requireText(update.name(), "规则组名称不能为空"));
        }
        if (update.description() != null) {
            entity.setDescription(DlpConfigValidation.blankToNull(update.description()));
        } else if (create) {
            entity.setDescription(null);
        }
        if (create || update.enabled() != null) {
            entity.setEnabled(update.enabled() == null || update.enabled());
        }
        if (create || update.priority() != null) {
            entity.setPriority(update.priority() != null ? update.priority() : 100);
        }
        entity.setUpdatedAt(Instant.now());
    }

    private void applyPolicyUpdate(DlpPolicyEntity entity,
                                   DlpPolicySettingsUpdate update,
                                   boolean create) {
        if (create || update.name() != null) {
            entity.setName(DlpConfigValidation.requireText(update.name(), "策略名称不能为空"));
        }
        if (update.description() != null) {
            entity.setDescription(DlpConfigValidation.blankToNull(update.description()));
        } else if (create) {
            entity.setDescription(null);
        }
        if (create || update.mode() != null) {
            entity.setMode(DlpConfigValidation.parseEnumOrDefault(DlpPolicyMode.class, update.mode(), DlpPolicyMode.ENFORCE, "DLP policy mode 无效").name());
        }
        if (update.direction() != null) {
            entity.setDirection(DlpConfigValidation.blankToNull(update.direction()) == null
                    ? null
                    : DlpConfigValidation.parseEnum(MailDirection.class, update.direction(), "DLP policy direction 无效").name());
        } else if (create) {
            entity.setDirection(null);
        }
        if (create || update.senderDomains() != null) {
            entity.setSenderDomains(new ArrayList<>(DlpConfigValidation.normalizeDomains(update.senderDomains())));
        }
        if (create || update.recipientDomains() != null) {
            entity.setRecipientDomains(new ArrayList<>(DlpConfigValidation.normalizeDomains(update.recipientDomains())));
        }
        if (create || update.senderAddressPatterns() != null) {
            DlpConfigValidation.validateWildcards(update.senderAddressPatterns());
            entity.setSenderAddressPatterns(new ArrayList<>(DlpConfigValidation.normalizeTextList(update.senderAddressPatterns())));
        }
        if (create || update.recipientAddressPatterns() != null) {
            DlpConfigValidation.validateWildcards(update.recipientAddressPatterns());
            entity.setRecipientAddressPatterns(new ArrayList<>(DlpConfigValidation.normalizeTextList(update.recipientAddressPatterns())));
        }
        if (create || update.attachmentRequired() != null) {
            entity.setAttachmentRequired(update.attachmentRequired() != null && update.attachmentRequired());
        }
        if (create || update.enabled() != null) {
            entity.setEnabled(update.enabled() == null || update.enabled());
        }
        if (create || update.priority() != null) {
            entity.setPriority(update.priority() != null ? update.priority() : 100);
        }
        entity.setUpdatedAt(Instant.now());
    }

    private List<String> ruleIdsForGroup(String groupId) {
        return entityManager.createQuery(
                        "SELECT i FROM DlpRuleGroupItemEntity i WHERE i.ruleGroupId = :groupId ORDER BY i.position ASC, i.createdAt ASC",
                        DlpRuleGroupItemEntity.class)
                .setParameter("groupId", groupId)
                .getResultList()
                .stream()
                .map(DlpRuleGroupItemEntity::getRuleId)
                .toList();
    }

    private List<String> ruleGroupIdsForPolicy(String policyId) {
        return entityManager.createQuery(
                        "SELECT i FROM DlpPolicyRuleGroupEntity i WHERE i.policyId = :policyId ORDER BY i.position ASC, i.createdAt ASC",
                        DlpPolicyRuleGroupEntity.class)
                .setParameter("policyId", policyId)
                .getResultList()
                .stream()
                .map(DlpPolicyRuleGroupEntity::getRuleGroupId)
                .toList();
    }

    private void replaceRuleGroupItems(String groupId, List<String> ruleIds) {
        removeRuleGroupItems(groupId);
        List<String> normalized = ruleIds == null ? List.of() : ruleIds;
        for (String ruleId : normalized) {
            patternStore.requirePattern(ruleId);
        }
        int position = 0;
        for (String ruleId : normalized) {
            DlpRuleGroupItemEntity item = new DlpRuleGroupItemEntity();
            item.setId(UUID.randomUUID().toString());
            item.setRuleGroupId(groupId);
            item.setRuleId(ruleId);
            item.setPosition(position++);
            item.setCreatedAt(Instant.now());
            entityManager.persist(item);
        }
    }

    private void replacePolicyRuleGroups(String policyId, List<String> ruleGroupIds) {
        removePolicyRuleGroups(policyId);
        List<String> normalized = ruleGroupIds == null ? List.of() : ruleGroupIds;
        for (String ruleGroupId : normalized) {
            requireRuleGroup(ruleGroupId);
        }
        int position = 0;
        for (String ruleGroupId : normalized) {
            DlpPolicyRuleGroupEntity item = new DlpPolicyRuleGroupEntity();
            item.setId(UUID.randomUUID().toString());
            item.setPolicyId(policyId);
            item.setRuleGroupId(ruleGroupId);
            item.setPosition(position++);
            item.setCreatedAt(Instant.now());
            entityManager.persist(item);
        }
    }

    private void removeRuleGroupItems(String groupId) {
        entityManager.createQuery("DELETE FROM DlpRuleGroupItemEntity i WHERE i.ruleGroupId = :groupId")
                .setParameter("groupId", groupId)
                .executeUpdate();
    }

    private void removePolicyRuleGroups(String policyId) {
        entityManager.createQuery("DELETE FROM DlpPolicyRuleGroupEntity i WHERE i.policyId = :policyId")
                .setParameter("policyId", policyId)
                .executeUpdate();
    }
}
