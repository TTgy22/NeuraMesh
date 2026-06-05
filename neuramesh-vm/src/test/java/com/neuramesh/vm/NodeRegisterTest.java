package com.neuramesh.vm;

import static com.neuramesh.vm.TestVmSupport.addr;
import static com.neuramesh.vm.TestVmSupport.tx;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neuramesh.core.CryptoUtils;
import com.neuramesh.core.TxType;
import com.neuramesh.vm.exception.VMException;
import com.neuramesh.vm.payload.NodeRegisterPayload;
import com.neuramesh.vm.state.GlobalState;
import com.neuramesh.vm.state.NodeState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NodeRegisterTest {

    private StateMachine sm() {
        return StateMachine.standard(TestVmSupport.validators(4).set());
    }

    private static byte[] fingerprint(int seed) {
        byte[] data = new byte[] {(byte) seed, 1, 2, 3};
        return CryptoUtils.sha256(data);
    }

    @Test
    @DisplayName("新指纹注册成功，NodeState 初始化且权重为 0")
    void register_new_node() {
        StateMachine sm = sm();
        GlobalState state = new GlobalState();
        byte[] node = addr(1);

        sm.apply(tx(TxType.NODE_REGISTER, node, node, 0,
                new NodeRegisterPayload(fingerprint(1), 5000.0).encode()), state);

        NodeState ns = state.getNode(node);
        assertThat(ns).isNotNull();
        assertThat(ns.getTotalWeight()).isZero();
        assertThat(state.isFingerprintRegistered(fingerprint(1))).isTrue();
    }

    @Test
    @DisplayName("重复指纹注册被拒绝")
    void duplicate_fingerprint_rejected() {
        StateMachine sm = sm();
        GlobalState state = new GlobalState();
        sm.apply(tx(TxType.NODE_REGISTER, addr(1), addr(1), 0,
                new NodeRegisterPayload(fingerprint(9), 1.0).encode()), state);

        // 不同节点地址，但相同指纹
        assertThatThrownBy(() -> sm.apply(tx(TxType.NODE_REGISTER, addr(2), addr(2), 0,
                new NodeRegisterPayload(fingerprint(9), 1.0).encode()), state))
                .isInstanceOf(VMException.class)
                .extracting(e -> ((VMException) e).getKind())
                .isEqualTo(VMException.Kind.DUPLICATE_REGISTRATION);
    }

    @Test
    @DisplayName("同一节点重复注册被拒绝")
    void duplicate_node_rejected() {
        StateMachine sm = sm();
        GlobalState state = new GlobalState();
        sm.apply(tx(TxType.NODE_REGISTER, addr(1), addr(1), 0,
                new NodeRegisterPayload(fingerprint(1), 1.0).encode()), state);
        assertThatThrownBy(() -> sm.apply(tx(TxType.NODE_REGISTER, addr(1), addr(1), 1,
                new NodeRegisterPayload(fingerprint(2), 1.0).encode()), state))
                .isInstanceOf(VMException.class)
                .extracting(e -> ((VMException) e).getKind())
                .isEqualTo(VMException.Kind.DUPLICATE_REGISTRATION);
    }
}
