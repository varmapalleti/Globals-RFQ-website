package com.globalrfq.rfq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import jakarta.annotation.PostConstruct;

import java.nio.file.Path;
import java.util.Set;

@ConfigurationProperties(prefix = "app.rfq")
public class RfqFileProperties {
    private String storageProvider = "local";
    private Path storageDirectory = Path.of("./data/rfq-files");
    private String s3Bucket = "";
    private String s3Prefix = "global-rfq";
    private String awsRegion = "";
    private long maxAttachmentSizeBytes = 26_214_400;
    private long previewTtlMinutes = 30;
    private Set<String> allowedAttachmentTypes = Set.of("pdf", "doc", "docx", "xls", "xlsx", "csv", "txt", "png", "jpg", "jpeg");

    public String getStorageProvider() { return storageProvider; }
    public Path getStorageDirectory() { return storageDirectory; }
    public String getS3Bucket() { return s3Bucket; }
    public String getS3Prefix() { return s3Prefix; }
    public String getAwsRegion() { return awsRegion; }
    public long getMaxAttachmentSizeBytes() { return maxAttachmentSizeBytes; }
    public long getPreviewTtlMinutes() { return previewTtlMinutes; }
    public Set<String> getAllowedAttachmentTypes() { return allowedAttachmentTypes; }

    public void setStorageProvider(String storageProvider) { this.storageProvider = storageProvider == null ? "" : storageProvider.trim().toLowerCase(java.util.Locale.ROOT); }
    public void setStorageDirectory(Path storageDirectory) { this.storageDirectory = storageDirectory; }
    public void setS3Bucket(String s3Bucket) { this.s3Bucket = s3Bucket; }
    public void setS3Prefix(String s3Prefix) { this.s3Prefix = s3Prefix == null ? "" : s3Prefix.replaceAll("^/+|/+$", ""); }
    public void setAwsRegion(String awsRegion) { this.awsRegion = awsRegion; }
    public void setMaxAttachmentSizeBytes(long maxAttachmentSizeBytes) { this.maxAttachmentSizeBytes = maxAttachmentSizeBytes; }
    public void setPreviewTtlMinutes(long previewTtlMinutes) { this.previewTtlMinutes = previewTtlMinutes; }
    public void setAllowedAttachmentTypes(Set<String> allowedAttachmentTypes) { this.allowedAttachmentTypes = allowedAttachmentTypes; }

    @PostConstruct
    void validate() {
        if (!"local".equals(storageProvider) && !"s3".equals(storageProvider)) {
            throw new IllegalStateException("RFQ_STORAGE_PROVIDER must be either 'local' or 's3'");
        }
        if (maxAttachmentSizeBytes < 1 || previewTtlMinutes < 1 || allowedAttachmentTypes == null || allowedAttachmentTypes.isEmpty()) {
            throw new IllegalStateException("RFQ file size, import-preview lifetime, and allowed file types must be configured with positive values");
        }
        if (s3Prefix != null && (s3Prefix.contains("..") || s3Prefix.contains("//"))) {
            throw new IllegalStateException("RFQ_S3_PREFIX contains an invalid path segment");
        }
    }
}
