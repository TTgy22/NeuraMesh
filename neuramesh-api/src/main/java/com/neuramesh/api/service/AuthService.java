package com.neuramesh.api.service;

import com.neuramesh.api.dto.RegisterRequest;
import com.neuramesh.api.dto.TokenResponse;
import com.neuramesh.api.security.CryptoBox;
import com.neuramesh.api.security.JwtUtil;
import com.neuramesh.core.CryptoUtils;
import com.neuramesh.vm.state.UserState;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.security.KeyPair;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务：注册（BCrypt 哈希 + 生成 ECDSA 密钥对 + 初始注资）、登录、刷新令牌。
 *
 * <p>用户存于 {@link com.neuramesh.vm.state.GlobalState}（经 ChainService）。私钥经用户密码 AES-256
 * 加密存储（{@link CryptoBox}）；地址由公钥派生，作为链上资金账户。
 */
@Service
public class AuthService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);
    private static final Set<String> ROLES = Set.of("VENDOR", "NODE_OPERATOR", "ADMIN");
    /** 厂商注册初始注资，便于演示购买资源组。 */
    private static final long VENDOR_INITIAL_FUNDING = 5_000_000L;

    private final ChainService chain;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;

    public AuthService(ChainService chain, PasswordEncoder encoder, JwtUtil jwtUtil) {
        this.chain = chain;
        this.encoder = encoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 注册新用户。
     *
     * @param req 注册请求
     * @return 令牌与用户信息
     */
    public synchronized TokenResponse register(RegisterRequest req) {
        if (req == null || req.username() == null || req.username().isBlank()
                || req.password() == null || req.password().length() < 6) {
            throw new IllegalArgumentException("用户名不可为空且密码至少 6 位");
        }
        String username = req.username().trim();
        if (chain.state().getUserByName(username) != null) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }
        String role = (req.role() == null || req.role().isBlank()) ? "VENDOR" : req.role().trim().toUpperCase();
        if (!ROLES.contains(role)) {
            throw new IllegalArgumentException("非法角色: " + role);
        }

        KeyPair kp = CryptoUtils.generateKeyPair();
        String publicKeyHex = CryptoUtils.toHex(kp.getPublic().getEncoded());
        String privKeyHex = CryptoUtils.toHex(kp.getPrivate().getEncoded());
        String encryptedPrivKey = CryptoBox.encrypt(privKeyHex, req.password());
        String addressHex = CryptoUtils.toHex(CryptoUtils.toAddress(kp.getPublic()));
        String passwordHash = encoder.encode(req.password());
        String userId = UUID.randomUUID().toString();

        UserState user = new UserState(userId, username, passwordHash, role,
                publicKeyHex, encryptedPrivKey, addressHex);
        chain.state().putUser(user);

        if ("VENDOR".equals(role)) {
            chain.fund(CryptoUtils.fromHex(addressHex), VENDOR_INITIAL_FUNDING);
        }

        LOG.info("用户注册成功 username={} role={} address={}", username, role, addressHex);
        return issueTokens(user);
    }

    /**
     * 登录。
     *
     * @param username 用户名
     * @param password 密码
     * @return 令牌与用户信息
     */
    public TokenResponse login(String username, String password) {
        if (username == null || password == null) {
            throw new IllegalArgumentException("用户名或密码为空");
        }
        UserState user = chain.state().getUserByName(username.trim());
        if (user == null || !encoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        return issueTokens(user);
    }

    /**
     * 用 refreshToken 换取新令牌。
     *
     * @param refreshToken 刷新令牌
     * @return 新令牌
     */
    public TokenResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken 为空");
        }
        try {
            Claims claims = jwtUtil.parse(refreshToken);
            if (!JwtUtil.TYPE_REFRESH.equals(claims.get(JwtUtil.CLAIM_TYPE, String.class))) {
                throw new IllegalArgumentException("非 refresh 令牌");
            }
            UserState user = chain.state().getUser(claims.getSubject());
            if (user == null) {
                throw new IllegalArgumentException("用户不存在");
            }
            return issueTokens(user);
        } catch (JwtException e) {
            throw new IllegalArgumentException("refreshToken 无效或已过期");
        }
    }

    private TokenResponse issueTokens(UserState user) {
        String access = jwtUtil.generateAccessToken(
                user.getUserId(), user.getUsername(), user.getRole(), user.getAddress());
        String refresh = jwtUtil.generateRefreshToken(
                user.getUserId(), user.getUsername(), user.getRole(), user.getAddress());
        return new TokenResponse(access, refresh, user.getUserId(), user.getUsername(),
                user.getRole(), "0x" + user.getAddress());
    }
}
