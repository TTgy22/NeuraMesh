package com.neuramesh.network.messages;

import com.neuramesh.network.NeuraMessage;

/**
 * 心跳 Pong。回应 Ping，同样携带高度。
 */
public class PongMessage extends NeuraMessage {

    private long currentHeight;

    public PongMessage() {
        super();
    }

    public PongMessage(long currentHeight) {
        super();
        this.currentHeight = currentHeight;
    }

    @Override
    public byte getTypeId() {
        return TYPE_PONG;
    }

    public long getCurrentHeight() {
        return currentHeight;
    }

    public void setCurrentHeight(long currentHeight) {
        this.currentHeight = currentHeight;
    }
}