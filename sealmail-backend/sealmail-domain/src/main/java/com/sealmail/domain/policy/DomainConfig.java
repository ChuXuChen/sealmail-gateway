package com.sealmail.domain.policy;

import com.sealmail.domain.policy.event.DomainConfigCreated;
import com.sealmail.domain.policy.event.DkimSettingChanged;
import com.sealmail.domain.policy.event.DomainConfigActivationChanged;
import com.sealmail.domain.policy.event.EncryptionPolicyChanged;
import com.sealmail.domain.policy.event.PreferredAlgorithmChanged;
import com.sealmail.domain.policy.event.SigningDisabled;
import com.sealmail.domain.policy.event.SigningEnabled;
import com.sealmail.domain.shared.model.AggregateRoot;

public class DomainConfig extends AggregateRoot<String> {

    private final String domain;
    private final boolean localDomain;
    private EncryptionPolicy encryptionPolicy;
    private PreferredAlgorithm preferredAlgorithm;
    private boolean signingEnabled;
    private boolean dkimEnabled;
    private boolean active;

    private DomainConfig(String id, String domain, boolean localDomain) {
        super(id);
        this.domain = domain;
        this.localDomain = localDomain;
        this.encryptionPolicy = EncryptionPolicy.ALLOW;
        this.preferredAlgorithm = PreferredAlgorithm.AUTO;
        this.signingEnabled = false;
        this.dkimEnabled = false;
        this.active = true;
    }

    public static DomainConfig create(String id, String domain, boolean localDomain) {
        String normalizedDomain = DomainName.requireValid(domain);
        DomainConfig config = new DomainConfig(id, normalizedDomain, localDomain);
        config.registerEvent(new DomainConfigCreated(id, normalizedDomain, localDomain));
        return config;
    }

    public void changePolicy(EncryptionPolicy newPolicy) {
        if (newPolicy == null) {
            throw new IllegalArgumentException("Policy cannot be null");
        }
        if (this.encryptionPolicy == newPolicy) {
            return;
        }
        EncryptionPolicy oldPolicy = this.encryptionPolicy;
        this.encryptionPolicy = newPolicy;
        registerEvent(new EncryptionPolicyChanged(getId(), oldPolicy, newPolicy));
    }

    public void enableSigning() {
        if (signingEnabled) {
            return;
        }
        this.signingEnabled = true;
        registerEvent(new SigningEnabled(getId()));
    }

    public void disableSigning() {
        if (!signingEnabled) {
            return;
        }
        this.signingEnabled = false;
        registerEvent(new SigningDisabled(getId()));
    }

    public void activate() {
        if (active) {
            return;
        }
        this.active = true;
        registerEvent(new DomainConfigActivationChanged(getId(), true));
    }

    public void deactivate() {
        if (!active) {
            return;
        }
        this.active = false;
        registerEvent(new DomainConfigActivationChanged(getId(), false));
    }

    public String getDomain() {
        return domain;
    }

    public boolean isLocalDomain() {
        return localDomain;
    }

    public EncryptionPolicy getEncryptionPolicy() {
        return encryptionPolicy;
    }

    public PreferredAlgorithm getPreferredAlgorithm() {
        return preferredAlgorithm;
    }

    public void changePreferredAlgorithm(PreferredAlgorithm preferredAlgorithm) {
        if (preferredAlgorithm == null) {
            throw new IllegalArgumentException("Preferred algorithm cannot be null");
        }
        if (this.preferredAlgorithm == preferredAlgorithm) {
            return;
        }
        PreferredAlgorithm oldAlgorithm = this.preferredAlgorithm;
        this.preferredAlgorithm = preferredAlgorithm;
        registerEvent(new PreferredAlgorithmChanged(getId(), oldAlgorithm, preferredAlgorithm));
    }

    public boolean isSigningEnabled() {
        return signingEnabled;
    }

    public boolean isDkimEnabled() {
        return dkimEnabled;
    }

    public void setDkimEnabled(boolean dkimEnabled) {
        if (this.dkimEnabled == dkimEnabled) {
            return;
        }
        this.dkimEnabled = dkimEnabled;
        registerEvent(new DkimSettingChanged(getId(), dkimEnabled));
    }

    public boolean isActive() {
        return active;
    }
}
