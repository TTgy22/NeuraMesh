package com.neuramesh.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neuramesh.core.CryptoUtils;
import com.neuramesh.core.NeuraException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CryptoUtilsTest {

    @Test
    @DisplayName("SHA-256: 1000 次随机输入哈希长度均为 32 字节")
    void sha256_random_inputs_have_length_32() {
        Random random = new Random(42L);
        for (int i = 0; i < 1000; i++) {
            byte[] input = new byte[1 + random.nextInt(256)];
            random.nextBytes(input);
            byte[] hash = CryptoUtils.sha256(input);
            assertThat(hash).hasSize(32);
        }
    }

    @Test
    @DisplayName("SHA-256: 已知向量 - 空串与 \"abc\" 哈希值符合标准")
    void sha256_known_vectors() {
        // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        assertThat(CryptoUtils.toHex(CryptoUtils.sha256(new byte[0])))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        // SHA-256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        assertThat(CryptoUtils.toHex(CryptoUtils.sha256("abc".getBytes(StandardCharsets.UTF_8))))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("ECDSA: 签名-验签 1000 次随机数据全部通过")
    void ecdsa_sign_verify_loop() {
        KeyPair keyPair = CryptoUtils.generateKeyPair();
        Random random = new Random(7L);
        for (int i = 0; i < 1000; i++) {
            byte[] data = new byte[1 + random.nextInt(128)];
            random.nextBytes(data);
            byte[] sig = CryptoUtils.sign(data, keyPair.getPrivate());
            assertThat(CryptoUtils.verify(data, sig, keyPair.getPublic())).isTrue();
        }
    }

    @Test
    @DisplayName("ECDSA: 数据被篡改后验签失败")
    void ecdsa_tampered_data_fails_verify() {
        KeyPair keyPair = CryptoUtils.generateKeyPair();
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] sig = CryptoUtils.sign(data, keyPair.getPrivate());
        byte[] tampered = "hellp".getBytes(StandardCharsets.UTF_8);
        assertThat(CryptoUtils.verify(tampered, sig, keyPair.getPublic())).isFalse();
    }

    @Test
    @DisplayName("ECDSA: 用其他公钥验签失败")
    void ecdsa_wrong_public_key_fails_verify() {
        KeyPair a = CryptoUtils.generateKeyPair();
        KeyPair b = CryptoUtils.generateKeyPair();
        byte[] data = "data".getBytes(StandardCharsets.UTF_8);
        byte[] sig = CryptoUtils.sign(data, a.getPrivate());
        assertThat(CryptoUtils.verify(data, sig, b.getPublic())).isFalse();
    }

    @Test
    @DisplayName("地址: 长度为 20 字节，不同公钥地址不同")
    void address_format_and_uniqueness() {
        Set<String> addresses = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            KeyPair kp = CryptoUtils.generateKeyPair();
            byte[] addr = CryptoUtils.toAddress(kp.getPublic());
            assertThat(addr).hasSize(CryptoUtils.ADDRESS_LENGTH);
            addresses.add(CryptoUtils.toHex(addr));
        }
        assertThat(addresses).hasSize(100);
    }

    @Test
    @DisplayName("地址: 同一公钥多次生成结果一致")
    void address_is_deterministic() {
        KeyPair kp = CryptoUtils.generateKeyPair();
        byte[] a1 = CryptoUtils.toAddress(kp.getPublic());
        byte[] a2 = CryptoUtils.toAddress(kp.getPublic());
        assertThat(a1).containsExactly(a2);
    }

    @Test
    @DisplayName("非法参数: null 输入抛出 NeuraException")
    void null_inputs_throw() {
        assertThatThrownBy(() -> CryptoUtils.sha256((byte[]) null))
                .isInstanceOf(NeuraException.class);
        assertThatThrownBy(() -> CryptoUtils.toAddress(null))
                .isInstanceOf(NeuraException.class);
    }
}
