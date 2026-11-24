package com.globalrfq.rfq.attachment.storage;

import com.globalrfq.rfq.common.error.RfqErrorCode;
import com.globalrfq.rfq.common.error.RfqException;
import com.globalrfq.rfq.config.RfqFileProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

@Component
@ConditionalOnProperty(prefix = "app.rfq", name = "storage-provider", havingValue = "s3")
public class S3RfqObjectStorage implements RfqObjectStorage {
    private final S3Client s3;
    private final RfqFileProperties properties;

    public S3RfqObjectStorage(S3Client s3, RfqFileProperties properties) {
        this.s3 = s3;
        this.properties = properties;
    }

    @Override
    public void put(ObjectKind kind, String key, Path source) throws IOException {
        String objectKey = objectKey(kind, key);
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(properties.getS3Bucket())
                            .key(objectKey)
                            .contentType("application/octet-stream")
                            .build(),
                    RequestBody.fromFile(source));
        } catch (RuntimeException exception) {
            throw new IOException("RFQ file could not be written to S3", exception);
        }
    }

    @Override
    public InputStream open(ObjectKind kind, String key) throws IOException {
        String objectKey = objectKey(kind, key);
        try {
            ResponseInputStream<GetObjectResponse> response = s3.getObject(GetObjectRequest.builder()
                    .bucket(properties.getS3Bucket())
                    .key(objectKey)
                    .build());
            return response;
        } catch (RuntimeException exception) {
            throw new IOException("RFQ file could not be read from S3", exception);
        }
    }

    @Override
    public void delete(ObjectKind kind, String key) {
        String objectKey = objectKey(kind, key);
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getS3Bucket())
                    .key(objectKey)
                    .build());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("RFQ file could not be deleted from S3", exception);
        }
    }

    private String objectKey(ObjectKind kind, String key) {
        LocalRfqObjectStorage.requireUuid(key);
        String prefix = properties.getS3Prefix();
        String namespace = kind == ObjectKind.ATTACHMENT ? "attachments" : "imports";
        String suffix = kind == ObjectKind.IMPORT ? ".stage" : "";
        return (prefix.isBlank() ? "" : prefix + "/") + namespace + "/" + key + suffix;
    }
}
