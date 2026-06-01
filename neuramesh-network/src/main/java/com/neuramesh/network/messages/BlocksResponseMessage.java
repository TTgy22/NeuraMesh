package com.neuramesh.network.messages;

import com.neuramesh.network.NeuraMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 区块同步响应：返回所请求高度区间的区块字节序列。
 *
 * <p>同样使用 byte[]（每个元素为单个 Block 的序列化字节）以解耦 Kryo 与 Block 的不可变设计。
 */
public class BlocksResponseMessage extends NeuraMessage {

    private List<byte[]> blockBytesList;

    public BlocksResponseMessage() {
        super();
        this.blockBytesList = new ArrayList<>();
    }

    public BlocksResponseMessage(List<byte[]> blockBytesList) {
        super();
        this.blockBytesList = (blockBytesList == null) ? new ArrayList<>() : new ArrayList<>(blockBytesList);
    }

    @Override
    public byte getTypeId() {
        return TYPE_BLOCKS_RESPONSE;
    }

    public List<byte[]> getBlockBytesList() {
        return (blockBytesList == null) ? Collections.emptyList()
                : Collections.unmodifiableList(blockBytesList);
    }

    public void setBlockBytesList(List<byte[]> blockBytesList) {
        this.blockBytesList = (blockBytesList == null) ? new ArrayList<>() : new ArrayList<>(blockBytesList);
    }
}