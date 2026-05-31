package com.neuramesh.core;

import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * 密码学静态工具类。
 *
 * <p>提供 SHA-256 哈希、ECDSA(secp256k1) 密钥生成/签名/验签，以及地址生成（公钥哈希前 20 字节）。
 * 所有方法均为静态、线程安全（每次调用新建 {@link MessageDigest}/{@link Signature} 实例）。
 */
public final class CryptoUtils {

    /** ECDSA 曲线名称。 */
    public static final String CURVE = "secp256k1";

    /** 签名算法。 */
    public static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    /** 地址长度（字节）。 */
    public static final int ADDRESS_LENGTH = 20;

    private static final String PROVIDER = "BC";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    static {
        if (Security.getProvider(PROVIDER) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private CryptoUtils() {
        throw new AssertionError("工具类禁止实例化");
    }

    /**
     * 计算输入的 SHA-256 哈希。
     *
     * @param input 输入字节（不可为 null）
     * @return 32 字节哈希值
     */
    public static byte[] sha256(byte[] input) {
        if (input == null) {
            throw new NeuraException("sha256 输入不可为 null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new NeuraException("SHA-256 算法不可用", e);
        }
    }

    /**
     * 对多段字节顺序拼接后计算 SHA-256（避免中间数组分配的便捷方法）。
     *
     * @param chunks 字节片段，按顺序参与哈希
     * @return 32 字节哈希值
     */
    public static byte[] sha256(byte[]... chunks) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] chunk : chunks) {
                if (chunk == null) {
                    throw new NeuraException("sha256 输入片段不可为 null");
                }
                digest.update(chunk);
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new NeuraException("SHA-256 算法不可用", e);
        }
    }

    /**
     * 生成 ECDSA(secp256k1) 密钥对。
     *
     * @return 新生成的密钥对
     */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", PROVIDER);
            generator.initialize(new ECGenParameterSpec(CURVE), new SecureRandom());
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new NeuraException("生成 ECDSA 密钥对失败", e);
        }
    }

    /**
     * 使用私钥对数据进行 ECDSA 签名。
     *
     * @param data       待签名数据
     * @param privateKey 私钥
     * @return DER 编码的签名字节
     */
    public static byte[] sign(byte[] data, PrivateKey privateKey) {
        if (data == null || privateKey == null) {
            throw new NeuraException("签名参数不可为 null");
        }
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM, PROVIDER);
            signature.initSign(privateKey);
            signature.update(data);
            return signature.sign();
        } catch (InvalidKeyException | SignatureException e) {
            throw new NeuraException("ECDSA 签名失败", e);
        } catch (Exception e) {
            throw new NeuraException("ECDSA 签名初始化失败", e);
        }
    }

    /**
     * 验证 ECDSA 签名。
     *
     * @param data      原始数据
     * @param sig       DER 编码的签名字节
     * @param publicKey 公钥
     * @return 验签是否通过
     */
    public static boolean verify(byte[] data, byte[] sig, PublicKey publicKey) {
        if (data == null || sig == null || publicKey == null) {
            throw new NeuraException("验签参数不可为 null");
        }
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM, PROVIDER);
            signature.initVerify(publicKey);
            signature.update(data);
            return signature.verify(sig);
        } catch (SignatureException e) {
            // 签名格式非法等情形视为验签不通过，而非抛出
            return false;
        } catch (InvalidKeyException e) {
            throw new NeuraException("验签公钥非法", e);
        } catch (Exception e) {
            throw new NeuraException("ECDSA 验签初始化失败", e);
        }
    }

    /**
     * 由公钥生成地址：取公钥未压缩点编码的 SHA-256 哈希的前 20 字节。
     *
     * @param publicKey ECDSA 公钥
     * @return 20 字节地址
     */
    public static byte[] toAddress(PublicKey publicKey) {
        if (publicKey == null) {
            throw new NeuraException("地址生成公钥不可为 null");
        }
        byte[] encoded = rawPublicKey(publicKey);
        byte[] hash = sha256(encoded);
        return Arrays.copyOfRange(hash, 0, ADDRESS_LENGTH);
    }

    /**
     * 提取公钥的未压缩点编码（0x04 || X || Y，共 65 字节）。
     *
     * @param publicKey ECDSA 公钥
     * @return 未压缩点编码字节
     */
    public static byte[] rawPublicKey(PublicKey publicKey) {
        if (publicKey instanceof ECPublicKey ecPublicKey) {
            return ecPublicKey.getQ().getEncoded(false);
        }
        // 兜底：使用 X.509 SubjectPublicKeyInfo 编码（仍然是确定性的）
        return publicKey.getEncoded();
    }

    /**
     * 从 X.509 编码字节恢复公钥。
     *
     * @param encoded X.509 SubjectPublicKeyInfo 编码
     * @return 公钥
     */
    public static PublicKey publicKeyFromEncoded(byte[] encoded) {
        try {
            KeyFactory factory = KeyFactory.getInstance("EC", PROVIDER);
            return factory.generatePublic(new X509EncodedKeySpec(encoded));
        } catch (Exception e) {
            throw new NeuraException("公钥反序列化失败", e);
        }
    }

    /**
     * 从 PKCS#8 编码字节恢复私钥。
     *
     * @param encoded PKCS#8 编码
     * @return 私钥
     */
    public static PrivateKey privateKeyFromEncoded(byte[] encoded) {
        try {
            KeyFactory factory = KeyFactory.getInstance("EC", PROVIDER);
            return factory.generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (Exception e) {
            throw new NeuraException("私钥反序列化失败", e);
        }
    }

    /**
     * 字节数组转小写十六进制字符串。
     *
     * @param bytes 输入字节（不可为 null）
     * @return 十六进制字符串
     */
    public static String toHex(byte[] bytes) {
        if (bytes == null) {
            throw new NeuraException("toHex 输入不可为 null");
        }
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}
