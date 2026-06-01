package com.neuramesh.consensus.bft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neuramesh.consensus.exception.ConsensusException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProposerSelectorTest {

    @Test
    @DisplayName("确定性：相同 round 始终选出相同提案人")
    void deterministic_selection() {
        TestValidators tv = TestValidators.equalWeight(8);
        ProposerSelector selector = new ProposerSelector(0);
        for (long r = 0; r < 50; r++) {
            Validator a = selector.selectProposer(r, tv.validators);
            Validator b = selector.selectProposer(r, tv.validators);
            assertThat(a).isEqualTo(b);
        }
    }

    @Test
    @DisplayName("等权重 8 验证者：100 轮轮询分布均匀")
    void equal_weight_round_robin_distribution() {
        TestValidators tv = TestValidators.equalWeight(8);
        ProposerSelector selector = new ProposerSelector(0);
        Map<String, Integer> counts = new HashMap<>();
        for (long h = 0; h < 800; h++) {
            Validator p = selector.selectProposer(h, tv.validators);
            counts.merge(p.getNodeIdHex(), 1, Integer::sum);
        }
        // 等权重下 800 轮应每人各 100 次
        assertThat(counts).hasSize(8);
        for (int c : counts.values()) {
            assertThat(c).isEqualTo(100);
        }
    }

    @Test
    @DisplayName("加权轮询：分布比例符合权重")
    void weighted_distribution_matches_weights() {
        // 权重 1:2:3:4，总权重 10
        TestValidators tv = TestValidators.withWeights(1, 2, 3, 4);
        ProposerSelector selector = new ProposerSelector(0);
        Map<String, Integer> counts = new HashMap<>();
        int rounds = 1000;
        for (long h = 0; h < rounds; h++) {
            Validator p = selector.selectProposer(h, tv.validators);
            counts.merge(p.getNodeIdHex(), 1, Integer::sum);
        }
        // round % 10 均匀覆盖 0..9 → 权重区间 [0)(1)(1..3)(3..6)(6..10) → 100,200,300,400
        assertThat(counts.get(tv.validatorOf(0).getNodeIdHex())).isEqualTo(100);
        assertThat(counts.get(tv.validatorOf(1).getNodeIdHex())).isEqualTo(200);
        assertThat(counts.get(tv.validatorOf(2).getNodeIdHex())).isEqualTo(300);
        assertThat(counts.get(tv.validatorOf(3).getNodeIdHex())).isEqualTo(400);
    }

    @Test
    @DisplayName("空验证者列表抛异常")
    void empty_validators_throws() {
        ProposerSelector selector = new ProposerSelector(0);
        assertThatThrownBy(() -> selector.selectProposer(0, java.util.List.of()))
                .isInstanceOf(ConsensusException.class);
    }
}
