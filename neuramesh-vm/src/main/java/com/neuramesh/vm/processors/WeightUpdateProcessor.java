package com.neuramesh.vm.processors;

import com.neuramesh.consensus.bft.ValidatorSet;
import com.neuramesh.core.Transaction;
import com.neuramesh.core.TxType;
import com.neuramesh.vm.TransactionProcessor;
import com.neuramesh.vm.WeightUpdateValidator;
import com.neuramesh.vm.exception.VMException;
import com.neuramesh.vm.payload.WeightUpdatePayload;
import com.neuramesh.vm.state.GlobalState;
import com.neuramesh.vm.state.NodeState;

/**
 * WEIGHT_UPDATE 处理器：交叉验证见证签名后更新节点四项分数并重算权重。
 *
 * <p>需 &ge; 2 个来自不同验证者的有效见证就同一分数达成一致，否则拒绝（单节点无法自改权重）。
 * 偏差见证者若本身为节点，质量分按 0.9 系数降权。
 */
public final class WeightUpdateProcessor implements TransactionProcessor {

    private static final double DEVIATION_PENALTY = 0.9;

    private final WeightUpdateValidator validator;

    public WeightUpdateProcessor(ValidatorSet validators) {
        this.validator = new WeightUpdateValidator(validators);
    }

    @Override
    public void process(Transaction tx, GlobalState state) {
        WeightUpdatePayload p = WeightUpdatePayload.decode(tx.getPayload());
        WeightUpdateValidator.Result result = validator.validate(p);
        if (!result.accepted()) {
            throw new VMException(VMException.Kind.INVALID_WEIGHT_ATTESTATION,
                    "权重见证未达成法定一致（需 ≥2 个不同验证者一致）");
        }
        NodeState node = state.getNode(p.targetNodeId());
        if (node == null) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "目标节点未注册");
        }
        node.setScores(p.hardware(), p.quality(), p.uptime(), p.bandwidth());

        // 偏差见证者降权（若其本身为已注册节点）
        for (String deviatingHex : result.deviatingValidatorIds()) {
            NodeState devNode = state.getNode(hexToBytes(deviatingHex));
            if (devNode != null) {
                devNode.penalizeQuality(DEVIATION_PENALTY);
            }
        }
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length() / 2;
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    @Override
    public TxType getType() {
        return TxType.WEIGHT_UPDATE;
    }
}
