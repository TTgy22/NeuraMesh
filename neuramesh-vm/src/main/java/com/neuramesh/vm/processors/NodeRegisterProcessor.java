package com.neuramesh.vm.processors;

import com.neuramesh.core.Transaction;
import com.neuramesh.core.TxType;
import com.neuramesh.vm.TransactionProcessor;
import com.neuramesh.vm.exception.VMException;
import com.neuramesh.vm.payload.NodeRegisterPayload;
import com.neuramesh.vm.state.GlobalState;
import com.neuramesh.vm.state.NodeState;

/**
 * NODE_REGISTER 处理器：解析设备指纹，校验唯一性，初始化 {@link NodeState}（初始权重 0）。
 *
 * <p>节点身份取 {@code tx.getFrom()}；同一指纹或同一节点不可重复注册。注册费当前为 0（债务）。
 */
public final class NodeRegisterProcessor implements TransactionProcessor {

    @Override
    public void process(Transaction tx, GlobalState state) {
        NodeRegisterPayload p = NodeRegisterPayload.decode(tx.getPayload());
        byte[] nodeId = tx.getFrom();

        if (state.getNode(nodeId) != null) {
            throw new VMException(VMException.Kind.DUPLICATE_REGISTRATION, "节点已注册");
        }
        if (state.isFingerprintRegistered(p.fingerprint())) {
            throw new VMException(VMException.Kind.DUPLICATE_REGISTRATION, "设备指纹已被注册");
        }
        if (p.hardwareScore() < 0) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "硬件分数不可为负");
        }

        // 初始权重为 0：分数随后经 WEIGHT_UPDATE（带见证）写入
        NodeState node = new NodeState(nodeId, p.fingerprint());
        state.putNode(node);
        state.registerFingerprint(p.fingerprint());
    }

    @Override
    public TxType getType() {
        return TxType.NODE_REGISTER;
    }
}
