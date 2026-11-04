package com.fashionagent.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhonesTest {

    @Test
    @DisplayName("하이픈·공백·+82를 지우고 숫자만 남긴다")
    void normalizes() {
        assertThat(Phones.normalize("010-1234-5678")).isEqualTo("01012345678");
        assertThat(Phones.normalize(" 010 1234 5678 ")).isEqualTo("01012345678");
        assertThat(Phones.normalize("+82 10-1234-5678")).isEqualTo("01012345678");
        assertThat(Phones.normalize("011-123-4567")).isEqualTo("0111234567");
    }

    @Test
    @DisplayName("휴대폰 번호가 아니면 bad request")
    void rejectsNonMobile() {
        for (String bad : new String[] {"02-123-4567", "1234", "010-1234-56789", "abc", ""}) {
            assertThatThrownBy(() -> Phones.normalize(bad)).isInstanceOf(HelperException.class).hasMessageContaining("휴대폰");
        }
    }
}
