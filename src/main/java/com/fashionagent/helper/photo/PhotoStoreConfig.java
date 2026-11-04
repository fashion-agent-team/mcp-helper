package com.fashionagent.helper.photo;

import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** R2 값(R2_ACCOUNT_ID 등)이 다 있으면 R2, 없으면 메모리에 둔다(개발용). */
@Configuration
public class PhotoStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(PhotoStoreConfig.class);

    @Bean
    PhotoStore photoStore(
            @Value("${helper.r2.account-id:}") String accountId,
            @Value("${helper.r2.access-key-id:}") String accessKeyId,
            @Value("${helper.r2.secret-access-key:}") String secretAccessKey,
            @Value("${helper.r2.bucket:fashion-agent-helper}") String bucket,
            @Value("${helper.r2.endpoint:}") String endpoint) {
        if (accountId.isBlank() || accessKeyId.isBlank() || secretAccessKey.isBlank()) {
            log.warn("R2 값이 없어서 사진을 메모리에 둡니다(개발용). 배포에서는 R2_ACCOUNT_ID, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY를 넣으세요.");
            return new MemoryPhotoStore();
        }
        String url = endpoint.isBlank() ? "https://" + accountId + ".r2.cloudflarestorage.com" : endpoint;
        S3Client s3 = S3Client.builder()
                .endpointOverride(URI.create(url))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .forcePathStyle(true)
                // R2는 AWS SDK 2.30부터 기본으로 붙는 새 체크섬을 다 받지 않아서, 필요할 때만 붙이게 한다(Cloudflare 문서 권장).
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(5))
                        .socketTimeout(Duration.ofSeconds(15)))
                .build();
        log.info("사진 보관: R2 버킷 {}", bucket);
        return new R2PhotoStore(s3, bucket);
    }
}
