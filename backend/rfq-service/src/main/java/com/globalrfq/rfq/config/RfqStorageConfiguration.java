package com.globalrfq.rfq.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.rfq", name = "storage-provider", havingValue = "s3")
public class RfqStorageConfiguration {

    @Bean(destroyMethod = "close")
    S3Client rfqS3Client(RfqFileProperties properties) {
        if (properties.getS3Bucket() == null || properties.getS3Bucket().isBlank()) {
            throw new IllegalStateException("RFQ_S3_BUCKET is required when RFQ_STORAGE_PROVIDER=s3");
        }
        if (properties.getAwsRegion() == null || properties.getAwsRegion().isBlank()) {
            throw new IllegalStateException("AWS_REGION is required when RFQ_STORAGE_PROVIDER=s3");
        }
        return S3Client.builder()
                .region(Region.of(properties.getAwsRegion()))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }
}
