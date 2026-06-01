package com.neuramesh.network;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuramesh.network.messages.CommitMessage;
import com.neuramesh.network.messages.PrePrepareMessage;
import com.neuramesh.network.messages.PrepareMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 验证 P2 新增的 3 种共识线路消息已注册且可经 Kryo 往返序列化（向后兼容，P1 消息不受影响）。
 */
class ConsensusMessageTest {

    @Test
    @DisplayName("共识消息 typeId 为 0x07/0x08/0x09 且已注册")
    void consensus_message_types_registered() {
        assertThat(NeuraMessage.TYPE_PRE_PREPARE).isEqualTo((byte) 0x07);
        assertThat(NeuraMessage.TYPE_PREPARE).isEqualTo((byte) 0x08);
        assertThat(NeuraMessage.TYPE_COMMIT).isEqualTo((byte) 0x09);
        assertThat(MessageRegistry.typeOf((byte) 0x07)).isEqualTo(PrePrepareMessage.class);
        assertThat(MessageRegistry.typeOf((byte) 0x08)).isEqualTo(PrepareMessage.class);
        assertThat(MessageRegistry.typeOf((byte) 0x09)).isEqualTo(CommitMessage.class);
        // P1 的 HELLO 仍为 0x06（无回退）
        assertThat(NeuraMessage.TYPE_HELLO).isEqualTo((byte) 0x06);
    }

    @Test
    @DisplayName("PrePrepareMessage Kryo 往返")
    void pre_prepare_round_trip() {
        PrePrepareMessage msg = new PrePrepareMessage(5L, new byte[] {1, 2, 3},
                new byte[] {4, 5}, new byte[] {6, 7, 8});
        NeuraMessage back = KryoSerialization.deserialize(KryoSerialization.serialize(msg));
        assertThat(back).isInstanceOf(PrePrepareMessage.class);
        PrePrepareMessage r = (PrePrepareMessage) back;
        assertThat(r.getHeight()).isEqualTo(5L);
        assertThat(r.getBlockBytes()).containsExactly(1, 2, 3);
        assertThat(r.getProposerId()).containsExactly(4, 5);
        assertThat(r.getProposerSignature()).containsExactly(6, 7, 8);
    }

    @Test
    @DisplayName("Prepare/Commit Kryo 往返")
    void prepare_commit_round_trip() {
        PrepareMessage prep = new PrepareMessage(new byte[] {9}, new byte[] {10}, new byte[] {11});
        NeuraMessage backPrep = KryoSerialization.deserialize(KryoSerialization.serialize(prep));
        assertThat(backPrep).isInstanceOf(PrepareMessage.class);
        assertThat(((PrepareMessage) backPrep).getBlockHash()).containsExactly(9);

        CommitMessage commit = new CommitMessage(new byte[] {12}, new byte[] {13}, new byte[] {14});
        NeuraMessage backCommit = KryoSerialization.deserialize(KryoSerialization.serialize(commit));
        assertThat(backCommit).isInstanceOf(CommitMessage.class);
        assertThat(((CommitMessage) backCommit).getValidatorId()).containsExactly(13);
    }
}
