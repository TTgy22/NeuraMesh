package com.neuramesh.network.messages;

import com.neuramesh.network.NeuraMessage;

/**
 * 心跳 Ping。携带本节点当前高度，用于快速发现分叉与心跳保活。
 */
public class PingMessage extends NeuraMessage {

    private long currentHeight;

    public PingMessage() {
        super();
    }

    public PingMessage(long currentHeight) {
        super();
        this.currentHeight = currentHeight;
    }

    @Override
    public byte getTypeId() {
        return TYPE_PING;
    }

    public long getCurrentHeight() {
        return currentHeight;
    }

    public void setCurrentHeight(long currentHeight) {
        this.currentHeight = currentHeight;
    }
}