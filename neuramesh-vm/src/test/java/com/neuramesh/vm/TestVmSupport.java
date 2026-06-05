package com.neuramesh.vm;

import com.neuramesh.consensus.bft.Validator;
import com.neuramesh.consensus.bft.ValidatorSet;
import com.neuramesh.core.CryptoUtils;
import com.neuramesh.core.Transaction;
import com.neuramesh.core.TxType;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

/**
 * VM 测试公用工具：构造地址、交易、验证者集与见证签名。
 */
final class TestVmSupport {

    private TestVmSupport() {
    }

    static byte[] addr(int seed) {
        byte[] a = new byte[CryptoUtils.ADDRESS_LENGTH];
        for (int i = 0; i < a.length; i++) {
            a[i] = (byte) (seed * 7 + i);
        }
        return a;
    }

    static Transaction tx(TxType type, byte[] from, byte[] to, long nonce, byte[] payload) {
        return Transaction.create(type, from, to, nonce, payload, 1_700_000_000_000L);
    }

    /** 生成 n 个验证者（带密钥对），等权重。 */
    static ValidatorContext validators(int n) {
        List<KeyPair> keys = new ArrayList<>();
        List<Validator> vs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            KeyPair kp = CryptoUtils.generateKeyPair();
            keys.add(kp);
            vs.add(new Validator(CryptoUtils.toAddress(kp.getPublic()), kp.getPublic(), 1, 0));
        }
        return new ValidatorContext(keys, new ValidatorSet(vs));
    }

    record ValidatorContext(List<KeyPair> keys, ValidatorSet set) {
        Attestation attest(int validatorIdx, byte[] targetNodeId, double claimedScore) {
            KeyPair kp = keys.get(validatorIdx);
            byte[] sig = CryptoUtils.sign(
                    Attestation.signingBytes(targetNodeId, claimedScore), kp.getPrivate());
            byte[] vid = CryptoUtils.toAddress(kp.getPublic());
            return new Attestation(vid, claimedScore, 1L, sig);
        }
    }
}
