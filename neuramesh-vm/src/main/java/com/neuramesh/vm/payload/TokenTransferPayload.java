package com.neuramesh.vm.payload;

import com.neuramesh.core.ByteUtils;
import com.neuramesh.vm.exception.VMException;
import java.nio.ByteBuffer;

/**
 * TOKEN_TRANSFER 负载：仅金额（from/to/nonce 取自 {@link com.neuramesh.core.Transaction}）。
 *
 * @param amount 转账金额
 */
public record TokenTransferPayload(long amount) {

    public byte[] encode() {
        return ByteUtils.longToBytes(amount);
    }

    public static TokenTransferPayload decode(byte[] bytes) {
        if (bytes == null || bytes.length != Long.BYTES) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "TOKEN_TRANSFER 负载需为 8 字节");
        }
        return new TokenTransferPayload(ByteBuffer.wrap(bytes)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN).getLong());
    }
}
