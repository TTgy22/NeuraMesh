package com.neuramesh.network.messages;

import com.neuramesh.network.NeuraMessage;

/**
 * 交易广播消息。
 *
 * <p>携带交易对象的序列化字节。这里使用字节数组而非 {@link com.neuramesh.core.Transaction}
 * 引用，避免 Kryo 在网络模块中直接序列化跨模块的不可变对象（Transaction 无 setter，Kryo 默认 setter
 * 复制器不友好）。{@code TxPool} 在收到后再调用 {@link com.neuramesh.core.Transaction#deserialize}
 * 之类的工厂还原（Pause 1 暂以 payload 字节直传）。
 */
public class TransactionGossipMessage extends NeuraMessage {

    private byte[] txBytes;
    private byte[] txId;

    public TransactionGossipMessage() {
        super();
    }

    public TransactionGossipMessage(byte[] txId, byte[] txBytes) {
        super();
        this.txId = (txId == null) ? new byte[0] : txId.clone();
        this.txBytes = (txBytes == null) ? new byte[0] : txBytes.clone();
    }

    @Override
    public byte getTypeId() {
        return TYPE_TX_GOSSIP;
    }

    public byte[] getTxBytes() {
        return (txBytes == null) ? new byte[0] : txBytes.clone();
    }

    public void setTxBytes(byte[] txBytes) {
        this.txBytes = (txBytes == null) ? new byte[0] : txBytes.clone();
    }

    public byte[] getTxId() {
        return (txId == null) ? new byte[0] : txId.clone();
    }

    public void setTxId(byte[] txId) {
        this.txId = (txId == null) ? new byte[0] : txId.clone();
    }
}