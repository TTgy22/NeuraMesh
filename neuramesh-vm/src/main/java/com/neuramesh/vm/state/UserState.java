package com.neuramesh.vm.state;

import com.neuramesh.vm.exception.VMException;

/**
 * 用户状态（P5 用户系统）。
 *
 * <p>用户为传统 API 账户（非链上注册），但纳入 {@link GlobalState} 的 Merkle 计算以获得确定性状态根。
 * 用户资金以链上 {@link AccountState} 为准（按 {@link #address} 寻址），本对象不重复存余额。
 *
 * <p>不可变值对象；{@code passwordHash} 为 BCrypt 哈希，{@code encryptedPrivKey} 为 AES-256 加密后的
 * ECDSA 私钥（派生自用户密码）。
 */
public final class UserState {

    private final String userId;
    private final String username;
    private final String passwordHash;
    private final String role;
    private final String publicKey;
    private final String encryptedPrivKey;
    private final String address;

    public UserState(String userId, String username, String passwordHash, String role,
                     String publicKey, String encryptedPrivKey, String address) {
        if (userId == null || userId.isBlank()) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "userId 不可为空");
        }
        if (username == null || username.isBlank()) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "username 不可为空");
        }
        if (role == null || role.isBlank()) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "role 不可为空");
        }
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash == null ? "" : passwordHash;
        this.role = role;
        this.publicKey = publicKey == null ? "" : publicKey;
        this.encryptedPrivKey = encryptedPrivKey == null ? "" : encryptedPrivKey;
        this.address = address == null ? "" : address;
    }

    /** 深拷贝（不可变，返回自身）。 */
    public UserState copy() {
        return this;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRole() {
        return role;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getEncryptedPrivKey() {
        return encryptedPrivKey;
    }

    public String getAddress() {
        return address;
    }
}
