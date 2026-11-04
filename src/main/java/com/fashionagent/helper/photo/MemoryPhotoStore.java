package com.fashionagent.helper.photo;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 개발·테스트용. R2 값이 없을 때 쓴다. 서버를 다시 켜면 사진이 사라진다. */
public class MemoryPhotoStore implements PhotoStore {

    private final Map<String, byte[]> photos = new ConcurrentHashMap<>();

    @Override
    public void put(String key, byte[] jpeg) {
        photos.put(key, jpeg.clone());
    }

    @Override
    public Optional<byte[]> get(String key) {
        return Optional.ofNullable(photos.get(key)).map(byte[]::clone);
    }

    @Override
    public void delete(String key) {
        photos.remove(key);
    }

    public int size() {
        return photos.size();
    }
}
