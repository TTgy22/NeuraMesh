package com.neuramesh.vm.payload;

import com.neuramesh.core.CryptoUtils;
import com.neuramesh.vm.Attestation;
import com.neuramesh.vm.exception.VMException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * WEIGHT_UPDATE 负载：目标节点、提议的四项分数、以及若干见证 {@link Attestation}。
 */
public record WeightUpdatePayload(byte[] targetNodeId, double hardware, double quality,
                                  double uptime, double bandwidth, List<Attestation> attestations) {

    public WeightUpdatePayload {
        if (targetNodeId == null || targetNodeId.length != CryptoUtils.ADDRESS_LENGTH) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD,
                    "targetNodeId 长度需为 " + CryptoUtils.ADDRESS_LENGTH);
        }
        targetNodeId = targetNodeId.clone();
        attestations = List.copyOf(attestations);
    }

    @Override
    public byte[] targetNodeId() {
        return targetNodeId.clone();
    }

    @Override
    public List<Attestation> attestations() {
        return Collections.unmodifiableList(attestations);
    }

    public byte[] encode() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bos)) {
            out.write(targetNodeId);
            out.writeDouble(hardware);
            out.writeDouble(quality);
            out.writeDouble(uptime);
            out.writeDouble(bandwidth);
            out.writeInt(attestations.size());
            for (Attestation a : attestations) {
                out.write(a.validatorId());
                out.writeDouble(a.claimedScore());
                out.writeLong(a.timestamp());
                byte[] sig = a.signature();
                out.writeInt(sig.length);
                out.write(sig);
            }
            out.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "WEIGHT_UPDATE 编码失败", e);
        }
    }

    public static WeightUpdatePayload decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "WEIGHT_UPDATE 负载为空");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            byte[] target = new byte[CryptoUtils.ADDRESS_LENGTH];
            in.readFully(target);
            double hw = in.readDouble();
            double q = in.readDouble();
            double up = in.readDouble();
            double bw = in.readDouble();
            int count = in.readInt();
            if (count < 0 || count > 100) {
                throw new VMException(VMException.Kind.INVALID_PAYLOAD, "见证数量非法: " + count);
            }
            List<Attestation> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                byte[] vid = new byte[CryptoUtils.ADDRESS_LENGTH];
                in.readFully(vid);
                double score = in.readDouble();
                long ts = in.readLong();
                int sigLen = in.readInt();
                if (sigLen < 0 || sigLen > 4096) {
                    throw new VMException(VMException.Kind.INVALID_PAYLOAD, "签名长度非法: " + sigLen);
                }
                byte[] sig = new byte[sigLen];
                in.readFully(sig);
                list.add(new Attestation(vid, score, ts, sig));
            }
            return new WeightUpdatePayload(target, hw, q, up, bw, list);
        } catch (IOException e) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "WEIGHT_UPDATE 解码失败", e);
        }
    }
}
