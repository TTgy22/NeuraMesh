package com.neuramesh.network.codec;

import com.neuramesh.core.Transaction;
import com.neuramesh.core.TxType;
import com.neuramesh.network.NetworkException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 交易的确定性字节编解码器。
 *
 * <p>{@link Transaction} 为不可变对象且无 setter，不适合 Kryo 字段复制；故采用显式编码：
 * 写入 type 序号、from、to、nonce、payload、signature、timestamp。
 *
 * <p>解码时通过 {@link Transaction#create} 重建（自动重算 txId），再 {@link Transaction#withSignature}
 * 附加签名，保证跨节点 txId 一致。
 */
public final class TransactionCodec {

    private TransactionCodec() {
        throw new AssertionError("工具类禁止实例化");
    }

    /**
     * 编码交易为字节。
     *
     * @param tx 交易
     * @return 字节数组
     */
    public static byte[] encode(Transaction tx) {
        if (tx == null) {
            throw new NetworkException("encode 交易不可为 null");
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bos)) {
            out.writeInt(tx.getType().ordinal());
            writeBytes(out, tx.getFrom());
            writeBytes(out, tx.getTo());
            out.writeLong(tx.getNonce());
            writeBytes(out, tx.getPayload());
            writeBytes(out, tx.getSignature());
            out.writeLong(tx.getTimestamp());
            out.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new NetworkException("交易编码失败", e);
        }
    }

    /**
     * 解码字节为交易。
     *
     * @param bytes 字节数组
     * @return 交易对象
     */
    public static Transaction decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new NetworkException("decode 字节不可为空");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int typeOrdinal = in.readInt();
            TxType[] types = TxType.values();
            if (typeOrdinal < 0 || typeOrdinal >= types.length) {
                throw new NetworkException("非法 TxType 序号: " + typeOrdinal);
            }
            byte[] from = readBytes(in);
            byte[] to = readBytes(in);
            long nonce = in.readLong();
            byte[] payload = readBytes(in);
            byte[] signature = readBytes(in);
            long timestamp = in.readLong();

            Transaction tx = Transaction.create(types[typeOrdinal], from, to, nonce, payload, timestamp);
            if (signature.length > 0) {
                tx = tx.withSignature(signature);
            }
            return tx;
        } catch (NetworkException e) {
            throw e;
        } catch (IOException e) {
            throw new NetworkException("交易解码失败", e);
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
