package com.neuramesh.consensus.bft;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuramesh.core.CryptoUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VoteCollectorTest {

    private static byte[] hash(int seed) {
        byte[] h = new byte[32];
        for (int i = 0; i < 32; i++) {
            h[i] = (byte) (seed + i);
        }
        return h;
    }

    private static byte[] validator(int seed) {
        byte[] v = new byte[CryptoUtils.ADDRESS_LENGTH];
        for (int i = 0; i < v.length; i++) {
            v[i] = (byte) (seed * 13 + i);
        }
        return v;
    }

    private static Vote vote(VoteType type, byte[] blockHash, int validatorSeed) {
        return new Vote(type, blockHash, validator(validatorSeed), new byte[] {1, 2, 3});
    }

    @Test
    @DisplayName("8 验证者 quorum=6：6 票 PREPARE 触发，5 票不触发")
    void quorum_threshold() {
        // quorum = floor(2*8/3)+1 = 6
        VoteCollector collector = new VoteCollector(6);
        byte[] bh = hash(1);

        for (int i = 0; i < 5; i++) {
            collector.addVote(vote(VoteType.PREPARE, bh, i));
        }
        assertThat(collector.count(CryptoUtils.toHex(bh), VoteType.PREPARE)).isEqualTo(5);
        assertThat(collector.hasQuorum(CryptoUtils.toHex(bh), VoteType.PREPARE)).isFalse();

        collector.addVote(vote(VoteType.PREPARE, bh, 5));
        assertThat(collector.hasQuorum(CryptoUtils.toHex(bh), VoteType.PREPARE)).isTrue();
    }

    @Test
    @DisplayName("去重：同一验证者同阶段重复投票只算一票")
    void duplicate_vote_deduplicated() {
        VoteCollector collector = new VoteCollector(6);
        byte[] bh = hash(2);
        assertThat(collector.addVote(vote(VoteType.PREPARE, bh, 0))).isTrue();
        assertThat(collector.addVote(vote(VoteType.PREPARE, bh, 0))).isFalse();
        assertThat(collector.count(CryptoUtils.toHex(bh), VoteType.PREPARE)).isEqualTo(1);
    }

    @Test
    @DisplayName("阶段隔离：PREPARE 与 COMMIT 分别计票")
    void phases_are_separate() {
        VoteCollector collector = new VoteCollector(6);
        byte[] bh = hash(3);
        for (int i = 0; i < 6; i++) {
            collector.addVote(vote(VoteType.PREPARE, bh, i));
        }
        assertThat(collector.hasQuorum(CryptoUtils.toHex(bh), VoteType.PREPARE)).isTrue();
        assertThat(collector.hasQuorum(CryptoUtils.toHex(bh), VoteType.COMMIT)).isFalse();
        assertThat(collector.count(CryptoUtils.toHex(bh), VoteType.COMMIT)).isZero();
    }

    @Test
    @DisplayName("区块隔离：不同区块哈希独立计票")
    void blocks_are_separate() {
        VoteCollector collector = new VoteCollector(6);
        byte[] bhA = hash(4);
        byte[] bhB = hash(40);
        for (int i = 0; i < 6; i++) {
            collector.addVote(vote(VoteType.PREPARE, bhA, i));
        }
        assertThat(collector.hasQuorum(CryptoUtils.toHex(bhA), VoteType.PREPARE)).isTrue();
        assertThat(collector.hasQuorum(CryptoUtils.toHex(bhB), VoteType.PREPARE)).isFalse();
    }
}
