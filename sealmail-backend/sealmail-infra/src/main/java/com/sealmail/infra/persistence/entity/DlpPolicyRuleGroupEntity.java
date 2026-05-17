package com.sealmail.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "dlp_policy_rule_group")
public class DlpPolicyRuleGroupEntity {

    @Id
    @Column(name = "id", length = 128)
    private String id;

    @Column(name = "policy_id", nullable = false, length = 128)
    private String policyId;

    @Column(name = "rule_group_id", nullable = false, length = 128)
    private String ruleGroupId;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
