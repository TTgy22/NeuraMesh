package com.neuramesh.consensus.bft;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuramesh.consensus.TxPool;
import com.neuramesh.consensus.block.BlockProducer;
import com.neuramesh.consensus.block.InMemoryBlockStore;
import com.neuramesh.core.Block;
import com.neuramesh.core.CryptoUtils;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class ByzantineTest {

    /** 不做任何转发的广播器（单节点拜占庭测试用）。 */
    private static final ConsensusBroadcaster NOOP = new ConsensusBroadcaster() {
        @Override
        public void broadcastPrePrepare(PrePrepare prePrepare) {
        }

        @Override
        public void broadcastPrepare(Vote vote) {
        }

        @Override
        public void broadcastCommit(Vote vote) {
        }
    };

    private static PrePrepare signedProposal(TestValidators tv, int proposerIdx, Block block) {
        byte[] sig = CryptoUtils.sign(block.getHash(), tv.keyOf(proposerIdx).getPrivate());
        return new PrePrepare(block.getHeight(),
                block, tv.validatorOf(proposerIdx).getNodeId(), sig);
    }

    @Test
    @Timeout(60)
    @DisplayName("提案人发送冲突 PrePrepare：第二个被拒绝，触发视图变更，不最终化")
    void conflicting_preprepare_rejected_and_view_change() {
        TestValidators tv = TestValidators.equalWeight(4);
        // seed 0：round 0 的提案人 = 验证者 0；诚实节点取验证者 1
        BFTConsensus honest = new BFTConsensus(tv.keyOf(1), tv.set, new ProposerSelector(0),
                new BlockProducer(new TxPool()), new InMemoryBlockStore(),
                new BlockFinality(), NOOP);
        honest.startConsensus(0);

        Block blockX = new Block(0L, new byte[32], List.of(), 1000L, new byte[0]);
        Block blockY = new Block(0L, new byte[32], List.of(), 2000L, new byte[0]);
        assertThat(CryptoUtils.toHex(blockX.getHash()))
                .isNotEqualTo(CryptoUtils.toHex(blockY.getHash()));

        // 第一个提案被接受
        honest.onPrePrepare(signedProposal(tv, 0, blockX));
        assertThat(honest.getState()).isEqualTo(ConsensusState.PREPARING);
        assertThat(honest.getEquivocationCount()).isZero();

        // 第二个冲突提案（同高度同提案人，不同区块）应被识别为等价物
        honest.onPrePrepare(signedProposal(tv, 0, blockY));

        assertThat(honest.getEquivocationCount()).isEqualTo(1);
        assertThat(honest.getView()).isEqualTo(1);
        assertThat(honest.getFinality().isFinalized(0)).isFalse();
    }

    @Test
    @Timeout(60)
    @DisplayName("非提案人伪造提案被拒绝（提案人不匹配）")
    void proposal_from_non_proposer_rejected() {
        TestValidators tv = TestValidators.equalWeight(4);
        // 诚实节点 = 验证者 2；round 0 提案人是验证者 0，由验证者 3 伪造提案
        BFTConsensus honest = new BFTConsensus(tv.keyOf(2), tv.set, new ProposerSelector(0),
                new BlockProducer(new TxPool()), new InMemoryBlockStore(),
                new BlockFinality(), NOOP);
        honest.startConsensus(0);

        Block block = new Block(0L, new byte[32], List.of(), 1000L, new byte[0]);
        // 用验证者 3 的身份与签名伪造（但 round 0 的合法提案人是 0）
        honest.onPrePrepare(signedProposal(tv, 3, block));

        // 提案人不匹配 → 未进入 PREPARING，未投票
        assertThat(honest.getState()).isEqualTo(ConsensusState.PROPOSING);
        assertThat(honest.getFinality().isFinalized(0)).isFalse();
    }
}
