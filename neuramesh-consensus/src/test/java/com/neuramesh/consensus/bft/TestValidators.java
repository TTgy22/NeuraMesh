package com.neuramesh.consensus.bft;

import com.neuramesh.core.CryptoUtils;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试用验证者工厂：生成 n 个带密钥对的验证者，权重可指定。
 */
final class TestValidators {

    final List<KeyPair> keyPairs = new ArrayList<>();
    final List<Validator> validators = new ArrayList<>();
    final ValidatorSet set;

    private TestValidators(int n, long[] weights) {
        for (int i = 0; i < n; i++) {
            KeyPair kp = CryptoUtils.generateKeyPair();
            keyPairs.add(kp);
            long w = (weights == null) ? 1 : weights[i];
            validators.add(new Validator(CryptoUtils.toAddress(kp.getPublic()), kp.getPublic(), w, 0));
        }
        this.set = new ValidatorSet(validators);
    }

    static TestValidators equalWeight(int n) {
        return new TestValidators(n, null);
    }

    static TestValidators withWeights(long... weights) {
        return new TestValidators(weights.length, weights);
    }

    KeyPair keyOf(int i) {
        return keyPairs.get(i);
    }

    Validator validatorOf(int i) {
        return validators.get(i);
    }
}
