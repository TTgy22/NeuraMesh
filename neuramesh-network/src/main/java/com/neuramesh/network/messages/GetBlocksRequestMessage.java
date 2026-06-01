package com.neuramesh.network.messages;

import com.neuramesh.network.NeuraMessage;

/**
 * 区块同步请求：请求 [startHeight, endHeight] 闭区间的区块。
 */
public class GetBlocksRequestMessage extends NeuraMessage {

    private long startHeight;
    private long endHeight;

    public GetBlocksRequestMessage() {
        super();
    }

    public GetBlocksRequestMessage(long startHeight, long endHeight) {
        super();
        this.startHeight = startHeight;
        this.endHeight = endHeight;
    }

    @Override
    public byte getTypeId() {
        return TYPE_GET_BLOCKS_REQ;
    }

    public long getStartHeight() {
        return startHeight;
    }

    public void setStartHeight(long startHeight) {
        this.startHeight = startHeight;
    }

    public long getEndHeight() {
        return endHeight;
    }

    public void setEndHeight(long endHeight) {
        this.endHeight = endHeight;
    }
}