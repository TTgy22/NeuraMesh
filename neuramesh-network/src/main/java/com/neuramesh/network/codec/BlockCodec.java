package com.neuramesh.network.codec;

import com.neuramesh.core.Block;
import com.neuramesh.core.Transaction;
import com.neuramesh.network.NetworkException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 区块的确定性字节编解码器。
 *
 * <p>仅编码构造区块所需的原始字段（height、prevHash、timestamp、validatorSig、交易列表）；
 * merkleRoot 与 hash 在解码重建 {@link Block} 时自动重算，因此哈希链在跨节点传输后保持一致。
 */
public final class BlockCodec {

    private BlockCodec() {
        throw new AssertionError("工具类禁止实例化");
    }

    /**
     * 编码区块为字节。
     *
     * @param block 区块
     * @return 字节数组
     */
    public static byte[] encode(Block block) {
        if (block == null) {
            throw new NetworkException("encode 区块不可为 null");
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bos)) {
            out.writeLong(block.getHeight());
            writeBytes(out, block.getPrevHash());
            out.writeLong(block.getTimestamp());
            writeBytes(out, block.getValidatorSig());
            List<Transaction> txs = block.getTransactions();
            out.writeInt(txs.size());
            for (Transaction tx : txs) {
                writeBytes(out, TransactionCodec.encode(tx));
            }
            out.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new NetworkException("区块编码失败", e);
        }
    }

    /**
     * 解码字节为区块。
     *
     * @param bytes 字节数组
     * @return 区块对象
     */
    public static Block decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new NetworkException("decode 字节不可为空");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            long height = in.readLong();
            byte[] prevHash = readBytes(in);
            long timestamp = in.readLong();
            byte[] validatorSig = readBytes(in);
            int txCount = in.readInt();
            if (txCount < 0 || txCount > 1_000_000) {
                throw new NetworkException("非法交易数: " + txCount);
            }
            List<Transaction> txs = new ArrayList<>(txCount);
            for (int i = 0; i < txCount; i++) {
                txs.add(TransactionCodec.decode(readBytes(in)));
            }
            return new Block(height, prevHash, txs, timestamp, validatorSig);
        } catch (NetworkException e) {
            throw e;
        } catch (IOException e) {
            throw new NetworkException("区块解码失败", e);
        }
    }

    private static void writeBytes(DataOutputStream out, byte[] data) throws IOException {
        out.writeInt(data.length);
        out.write(data);
    }

    private static byte[] readBytes(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len < 0 || len > 16 * 1024 * 1024) {
            throw new NetworkException("非法字节长度: " + len);
        }
        byte[] data = new byte[len];
        in.readFully(data);
        return data;
    }
}
