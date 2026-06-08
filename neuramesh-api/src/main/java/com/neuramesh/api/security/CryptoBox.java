package com.neuramesh.api.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 对称加密盒：AES-256-GCM，密钥由用户密码经 PBKDF2 派生。用于加密存储 ECDSA 私钥。
 *
 * <p>输出格式（Base64）：{@code salt(16) || iv(12) || ciphertext+tag}。解密需相同密码。
 *
 * <p>注：赛事 Demo 简化，密码即密钥来源；真实场景应配独立 KMS / 硬件密钥。
 */
public final class CryptoBox {

    private static final int SALT_LEN = 16;
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BITS = 256;
    private static final int PBKDF2_ITERS = 65536;
    private static final SecureRandom RND = new SecureRandom();

    private CryptoBox() {
    }

    /**
     * 用密码加密明文。
     *
     * @param plaintext 明文
     * @param password  口令
     * @return Base64 封装的密文
     */
    public static String encrypt(String plaintext, String password) {
        try {
            byte[] salt = new byte[SALT_LEN];
            RND.nextBytes(salt);
            byte[] iv = new byte[IV_LEN];
            RND.nextBytes(iv);
            SecretKeySpec key = deriveKey(password, salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[salt.length + iv.length + ct.length];
            System.arraycopy(salt, 0, out, 0, salt.length);
            System.arraycopy(iv, 0, out, salt.length, iv.length);
            System.arraycopy(ct, 0, out, salt.length + iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    /**
     * 用密码解密。
     *
     * @param packed   Base64 封装密文
     * @param password 口令
     * @return 明文
     */
    public static String decrypt(String packed, String password) {
        try {
            byte[] all = Base64.getDecoder().decode(packed);
            byte[] salt = new byte[SALT_LEN];
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(all, 0, salt, 0, SALT_LEN);
            System.arraycopy(all, SALT_LEN, iv, 0, IV_LEN);
            byte[] ct = new byte[all.length - SALT_LEN - IV_LEN];
            System.arraycopy(all, SALT_LEN + IV_LEN, ct, 0, ct.length);
            SecretKeySpec key = deriveKey(password, salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("解密失败", e);
        }
    }

    private static SecretKeySpec deriveKey(String password, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERS, KEY_BITS);
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    }
}
