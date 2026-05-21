package com.sealmail.infra.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "sealmail.attachment-security")
public class AttachmentSecurityProperties {

    private int maxAttachmentBytes = 5 * 1024 * 1024;
    private long maxTotalAttachmentBytes = 25 * 1024 * 1024L;
    private int maxAttachmentCount = 20;
    private int maxZipEntries = 20;
    private long maxZipExpandedBytes = 10 * 1024 * 1024L;
    private int maxAttachmentDepth = 8;
    private List<String> highRiskExtensions = new ArrayList<>(List.of(
            "exe",
            "dll",
            "js",
            "vbs",
            "ps1",
            "bat",
            "cmd",
            "scr",
            "jar",
            "msi",
            "com"));
    private String mimeMismatchAction = "WARN";
    private String highRiskExtensionAction = "QUARANTINE";
    private String doubleExtensionAction = "QUARANTINE";
    private String encryptedArchiveAction = "QUARANTINE";
    private String sizeLimitAction = "BLOCK";
    private String totalSizeLimitAction = "BLOCK";
    private String archiveLimitAction = "BLOCK";

    public int getMaxAttachmentBytes() {
        return maxAttachmentBytes;
    }

    public void setMaxAttachmentBytes(int maxAttachmentBytes) {
        this.maxAttachmentBytes = maxAttachmentBytes;
    }

    public long getMaxTotalAttachmentBytes() {
        return maxTotalAttachmentBytes;
    }

    public void setMaxTotalAttachmentBytes(long maxTotalAttachmentBytes) {
        this.maxTotalAttachmentBytes = maxTotalAttachmentBytes;
    }

    public int getMaxAttachmentCount() {
        return maxAttachmentCount;
    }

    public void setMaxAttachmentCount(int maxAttachmentCount) {
        this.maxAttachmentCount = maxAttachmentCount;
    }

    public int getMaxZipEntries() {
        return maxZipEntries;
    }

    public void setMaxZipEntries(int maxZipEntries) {
        this.maxZipEntries = maxZipEntries;
    }

    public long getMaxZipExpandedBytes() {
        return maxZipExpandedBytes;
    }

    public void setMaxZipExpandedBytes(long maxZipExpandedBytes) {
        this.maxZipExpandedBytes = maxZipExpandedBytes;
    }

    public int getMaxAttachmentDepth() {
        return maxAttachmentDepth;
    }

    public void setMaxAttachmentDepth(int maxAttachmentDepth) {
        this.maxAttachmentDepth = maxAttachmentDepth;
    }

    public List<String> getHighRiskExtensions() {
        return highRiskExtensions;
    }

    public void setHighRiskExtensions(List<String> highRiskExtensions) {
        this.highRiskExtensions = highRiskExtensions;
    }

    public String getMimeMismatchAction() {
        return mimeMismatchAction;
    }

    public void setMimeMismatchAction(String mimeMismatchAction) {
        this.mimeMismatchAction = mimeMismatchAction;
    }

    public String getHighRiskExtensionAction() {
        return highRiskExtensionAction;
    }

    public void setHighRiskExtensionAction(String highRiskExtensionAction) {
        this.highRiskExtensionAction = highRiskExtensionAction;
    }

    public String getDoubleExtensionAction() {
        return doubleExtensionAction;
    }

    public void setDoubleExtensionAction(String doubleExtensionAction) {
        this.doubleExtensionAction = doubleExtensionAction;
    }

    public String getEncryptedArchiveAction() {
        return encryptedArchiveAction;
    }

    public void setEncryptedArchiveAction(String encryptedArchiveAction) {
        this.encryptedArchiveAction = encryptedArchiveAction;
    }

    public String getSizeLimitAction() {
        return sizeLimitAction;
    }

    public void setSizeLimitAction(String sizeLimitAction) {
        this.sizeLimitAction = sizeLimitAction;
    }

    public String getTotalSizeLimitAction() {
        return totalSizeLimitAction;
    }

    public void setTotalSizeLimitAction(String totalSizeLimitAction) {
        this.totalSizeLimitAction = totalSizeLimitAction;
    }

    public String getArchiveLimitAction() {
        return archiveLimitAction;
    }

    public void setArchiveLimitAction(String archiveLimitAction) {
        this.archiveLimitAction = archiveLimitAction;
    }
}
