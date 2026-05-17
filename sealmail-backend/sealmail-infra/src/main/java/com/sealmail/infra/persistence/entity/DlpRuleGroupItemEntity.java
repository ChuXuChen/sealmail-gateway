package com.sealmail.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "dlp_rule_group_item")
public class DlpRuleGroupItemEntity {

    @Id
    @Column(name = "id", length = 128)
    private String id;

    @Column(name = "rule_group_id", nullable = false, length = 128)
    private String ruleGroupId;

    @Column(name = "rule_id", nullable = false, length = 128)
    private String ruleId;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
