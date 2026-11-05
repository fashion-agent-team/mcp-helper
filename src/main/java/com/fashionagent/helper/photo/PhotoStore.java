package com.fashionagent.helper.photo;

import java.util.Optional;

/** 가족에게 보여 줄 사진을 잠깐 두는 곳. 약속 문서상 사진을 저장하는 유일한 예외라 24시간 안에 반드시 지운다. */
public interface PhotoStore {

    void put(String key, byte[] jpeg);

    Optional<byte[]> get(String key);

    /** 없는 키를 지워도 오류가 아니다. */
    void delete(String key);
}
