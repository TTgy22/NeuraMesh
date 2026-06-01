package com.neuramesh.network.messages;

import com.neuramesh.network.NeuraMessage;

/**
 * 握手消息：连接建立后双方互发，用于交换 NodeId 与监听端口。
 */
public class HelloMessage extends NeuraMessage {

    private byte[] nodeId;
    private int listeningPort;
    private long currentHeight;

    public HelloMessage() {
        super();
    }

    public HelloMessage(byte[] nodeId, int listeningPort, long currentHeight) {
        super();
        this.nodeId = (nodeId == null) ? new byte[0] : nodeId.clone();
        this.listeningPort = listeningPort;
        this.currentHeight = currentHeight;
    }

    @Override
    public byte getTypeId() {
        return TYPE_HELLO;
    }

    public byte[] getNodeId() {
        return (nodeId == null) ? new byte[0] : nodeId.clone();
    }

    public void setNodeId(byte[] nodeId) {
        this.nodeId = (nodeId == null) ? new byte[0] : nodeId.clone();
    }

    public int getListeningPort() {
        return listeningPort;
    }

    public void setListeningPort(int listeningPort) {
        this.listeningPort = listeningPort;
    }

    public long getCurrentHeight() {
        return currentHeight;
    }

    public void setCurrentHeight(long currentHeight) {
        this.currentHeight = currentHeight;
    }
}