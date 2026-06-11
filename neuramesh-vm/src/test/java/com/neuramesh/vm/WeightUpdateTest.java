package com.neuramesh.vm;

import static com.neuramesh.vm.TestVmSupport.addr;
import static com.neuramesh.vm.TestVmSupport.tx;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neuramesh.core.CryptoUtils;
import com.neuramesh.core.TxType;
import com.neuramesh.vm.TestVmSupport.ValidatorContext;
import com.neuramesh.vm.exception.VMException;
import com.neuramesh.vm.payload.NodeRegisterPayload;
import com.neuramesh.vm.payload.WeightUpdatePayload;
import com.neuramesh.vm.state.GlobalState;
import com.neuramesh.vm.state.NodeState;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeightUpdateTest {

    private final ValidatorContext vc = TestVmSupport.validators(4);

    private WeightUpdatePayload payload(byte[] target, List<Attestation> atts) {
        return new WeightUpdatePayload(target, 100, 200, 300, 400, atts);
    }

    @Test
    @DisplayName("交叉验证：2 个一致 1 个偏差 → 通过，偏差者计 1")
    void two_agree_one_deviate_accepted() {
        WeightUpdateValidator validator = new WeightUpdateValidator(vc.set());
        byte[] target = addr(100);
        WeightUpdatePayload p = payload(target, List.of(
                vc.attest(0, target, 80.0),
                vc.attest(1, target, 80.0),
                vc.attest(2, target, 50.0)));
        WeightUpdateValidator.Result r = validator.validate(p);
        assertThat(r.accepted()).isTrue();
        assertThat(r.agreedScore()).isEqualTo(80.0);
        assertThat(r.deviatingValidatorIds()).hasSize(1);
    }

    @Test
    @DisplayName("交叉验证：3 个全偏差 → 拒绝")
    void all_different_rejected() {
        WeightUpdateValidator validator = new WeightUpdateValidator(vc.set());
        byte[] target = addr(100);
        WeightUpdatePayload p = payload(target, List.of(
                vc.attest(0, target, 10.0),
                vc.attest(1, target, 20.0),
                vc.attest(2, target, 30.0)));
        assertThat(validator.validate(p).accepted()).isFalse();
    }

    @Test
    @DisplayName("交叉验证：仅 1 个签名 → 拒绝")
    void single_attestation_rejected() {
        WeightUpdateValidator validator = new WeightUpdateValidator(vc.set());
        byte[] target = addr(100);
        WeightUpdatePayload p = payload(target, List.of(vc.attest(0, target, 80.0)));
        assertThat(validator.validate(p).accepted()).isFalse();
    }

    @Test
    @DisplayName("交叉验证：非验证者/无效签名被过滤后不足 2 → 拒绝")
    void invalid_signature_filtered() {
        WeightUpdateValidator validator = new WeightUpdateValidator(vc.set());
        byte[] target = addr(100);
        Attestation good = vc.attest(0, target, 80.0);
        // 伪造签名（非验证者地址 + 垃圾签名）
        Attestation bad = new Attestation(addr(200), 80.0, 1L, new byte[] {1, 2, 3});
        WeightUpdatePayload p = payload(target, List.of(good, bad));
        assertThat(validator.validate(p).accepted()).isFalse();
    }

    @Test
    @DisplayName("处理器端到端：见证通过后节点权重按公式更新")
    void processor_updates_weight() {
        StateMachine sm = StateMachine.standard(vc.set());
        GlobalState state = new GlobalState();
        byte[] node = addr(100);

        // 先注册节点（nonce 0 → 1）
        sm.apply(tx(TxType.NODE_REGISTER, node, node, 0,
                new NodeRegisterPayload(CryptoUtils.sha256(new byte[] {7}), 1.0).encode()), state);

        // WEIGHT_UPDATE：2 个一致见证（80.0）
        WeightUpdatePayload p = payload(node, List.of(
                vc.attest(0, node, 80.0),
                vc.attest(1, node, 80.0)));
        sm.apply(tx(TxType.WEIGHT_UPDATE, node, node, 1, p.encode()), state);

        NodeState ns = state.getNode(node);
        // 100*0.3 + 200*0.4 + 300*0.2 + 400*0.1 = 30 + 80 + 60 + 40 = 210
        assertThat(ns.getTotalWeight()).isEqualTo(210.0);
    }

    @Test
    @DisplayName("处理器：见证不足时整体失败回滚")
    void processor_rejects_insufficient_attestation() {
        StateMachine sm = StateMachine.standard(vc.set());
        GlobalState state = new GlobalState();
        byte[] node = addr(100);
        sm.apply(tx(TxType.NODE_REGISTER, node, node, 0,
                new NodeRegisterPayload(CryptoUtils.sha256(new byte[] {7}), 1.0).encode()), state);

        WeightUpdatePayload p = payload(node, List.of(
                vc.attest(0, node, 10.0),
                vc.attest(1, node, 20.0),
                vc.attest(2, node, 30.0)));
        // 回滚基准：注册即赋初始权重 hw*0.3（此处 hw=1.0 → 0.3）
        double weightAfterRegister = state.getNode(node).getTotalWeight();
        assertThat(weightAfterRegister).isEqualTo(0.3);
        assertThatThrownBy(() -> sm.apply(tx(TxType.WEIGHT_UPDATE, node, node, 1, p.encode()), state))
                .isInstanceOf(VMException.class)
                .extracting(e -> ((VMException) e).getKind())
                .isEqualTo(VMException.Kind.INVALID_WEIGHT_ATTESTATION);
        // 节点权重维持注册初始值（回滚）
        assertThat(state.getNode(node).getTotalWeight()).isEqualTo(weightAfterRegister);
    }
}
