package com.fashionagent.helper.photo;

import java.util.Optional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * Cloudflare R2 (S3 호환 API). 버킷은 비공개로 두고 사진은 이 서버의 /h/&lt;token&gt;/photo로만 내보낸다.
 * 서명된 공개 URL을 쓰지 않는 이유: 답을 받는 즉시 사진을 지우고 링크를 막을 수 있게 하려고.
 * 버킷에는 24시간 수명 주기 규칙도 걸어 둔다(이 서버가 꺼져 있어도 지워지게). docs/DEVELOPMENT.md 참고.
 */
public class R2PhotoStore implements PhotoStore {

    private final S3Client s3;
    private final String bucket;

    public R2PhotoStore(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, byte[] jpeg) {
        s3.putObject(b -> b.bucket(bucket).key(key).contentType("image/jpeg"), RequestBody.fromBytes(jpeg));
    }

    @Override
    public Optional<byte[]> get(String key) {
        try {
            return Optional.of(s3.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(b -> b.bucket(bucket).key(key));
    }
}
