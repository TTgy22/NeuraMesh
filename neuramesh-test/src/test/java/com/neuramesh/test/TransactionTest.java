package com.neuramesh.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neuramesh.core.CryptoUtils;
import com.neuramesh.core.NeuraException;
import com.neuramesh.core.Transaction;
import com.neuramesh.core.TxType;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransactionTest {

    private static byte[] addr(int seed) {
        byte[] a = new byte[CryptoUtils.ADDRESS_LENGTH];
        for (int i = 0; i < a.length; i++) {
            a[i] = (byte) (seed + i);
        }
        return a;
    }

    @Test
    @DisplayName("4 种 TxType 均可创建交易，txId 长度 32")
    void all_tx_types_constructible() {
        for (TxType type : TxType.values()) {
            Transaction tx = Transaction.create(type, addr(1), addr(2), 0,
                    new byte[] {1, 2, 3}, 1_700_000_000_000L);
            assertThat(tx.getType()).isEqualTo(type);
            assertThat(tx.getTxId()).hasSize(32);
        }
        assertThat(TxType.values()).hasSize(4);
    }

    @Test
    @DisplayName("txId 唯一性：1000 笔不同 nonce 交易的 txId 全不相同")
    void txid_unique_across_distinct_nonces() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            Transaction tx = Transaction.create(TxType.TOKEN_TRANSFER, addr(1), addr(2),
                    i, new byte[0], 1_700_000_000_000L);
            ids.add(CryptoUtils.toHex(tx.getTxId()));
        }
        assertThat(ids).hasSize(1000);
    }

    @Test
    @DisplayName("txId 确定性：相同字段计算结果一致")
    void txid_deterministic() {
        Transaction t1 = Transaction.create(TxType.NODE_REGISTER, addr(1), addr(2),
                42L, "payload".getBytes(StandardCharsets.UTF_8), 1_700_000_000_000L);
        Transaction t2 = Transaction.create(TxType.NODE_REGISTER, addr(1), addr(2),
                42L, "payload".getBytes(StandardCharsets.UTF_8), 1_700_000_000_000L);
        assertThat(t1.getTxId()).containsExactly(t2.getTxId());
    }

    @Test
    @DisplayName("签名-验签闭环：附加签名后可用公钥验证 signingBytes")
    void sign_verify_round_trip() {
        KeyPair kp = CryptoUtils.generateKeyPair();
        byte[] from = CryptoUtils.toAddress(kp.getPublic());
        Transaction unsigned = Transaction.create(TxType.WEIGHT_UPDATE, from, addr(2), 1L,
                "weights".getBytes(StandardCharsets.UTF_8), 1_700_000_000_000L);
        byte[] sig = CryptoUtils.sign(unsigned.signingBytes(), kp.getPrivate());
        Transaction signed = unsigned.withSignature(sig);

        assertThat(signed.getSignature()).isEqualTo(sig);
        assertThat(signed.getTxId()).containsExactly(unsigned.getTxId());
        assertThat(CryptoUtils.verify(signed.signingBytes(), signed.getSignature(), kp.getPublic()))
                .isTrue();
    }

    @Test
    @DisplayName("不可变性：getter 返回防御性拷贝，外部修改不影响内部状态")
    void immutability_defensive_copy() {
        Transaction tx = Transaction.create(TxType.TASK_SETTLE, addr(1), addr(2), 0,
                new byte[] {1, 2, 3}, 1L);
        byte[] payload = tx.getPayload();
        payload[0] = 99;
        assertThat(tx.getPayload()[0]).isEqualTo((byte) 1);

        byte[] id = tx.getTxId();
        id[0] = 99;
        assertThat(tx.getTxId()[0]).isNotEqualTo((byte) 99);
    }

    @Test
    @DisplayName("非法参数：地址长度错误、null payload 抛出异常")
    void invalid_arguments() {
        assertThatThrownBy(() -> Transaction.create(TxType.TOKEN_TRANSFER, new byte[10], addr(2), 0,
                new byte[0], 0L)).isInstanceOf(NeuraException.class);
        assertThatThrownBy(() -> Transaction.create(TxType.TOKEN_TRANSFER, addr(1), null, 0,
                new byte[0], 0L)).isInstanceOf(NeuraException.class);
        assertThatThrownBy(() -> Transaction.create(TxType.TOKEN_TRANSFER, addr(1), addr(2), 0,
                null, 0L)).isInstanceOf(NeuraException.class);
    }
}
