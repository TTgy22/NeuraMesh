package com.neuramesh.api.service;

import com.neuramesh.core.CryptoUtils;
import com.neuramesh.vm.state.UserState;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 用户服务：当前用户信息与余额查询（余额取链上账户）。
 */
@Service
public class UserService {

    private final ChainService chain;

    public UserService(ChainService chain) {
        this.chain = chain;
    }

    /**
     * 当前用户公开信息（不含密码哈希/私钥）。
     *
     * @param userId 用户 id
     * @return 信息映射；用户不存在返回 null
     */
    public Map<String, Object> profile(String userId) {
        UserState u = chain.state().getUser(userId);
        if (u == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", u.getUserId());
        m.put("username", u.getUsername());
        m.put("role", u.getRole());
        m.put("address", "0x" + u.getAddress());
        m.put("publicKey", u.getPublicKey());
        m.put("balance", balanceOf(u));
        return m;
    }

    /**
     * 用户链上余额。
     *
     * @param userId 用户 id
     * @return 余额；用户不存在返回 -1
     */
    public long balance(String userId) {
        UserState u = chain.state().getUser(userId);
        return u == null ? -1 : balanceOf(u);
    }

    private long balanceOf(UserState u) {
        if (u.getAddress().isBlank()) {
            return 0;
        }
        return chain.balanceOf(CryptoUtils.fromHex(u.getAddress()));
    }
}
