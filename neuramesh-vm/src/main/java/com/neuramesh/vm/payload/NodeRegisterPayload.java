package com.neuramesh.vm.payload;

import com.neuramesh.vm.exception.VMException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * NODE_REGISTER 负载：设备指纹 + 初始硬件分数。
 *
 * @param fingerprint   设备指纹（SHA-256，32 字节）
 * @param hardwareScore 初始硬件分数（来自 DeviceBenchmark）
 */
public record NodeRegisterPayload(byte[] fingerprint, double hardwareScore) {

    public NodeRegisterPayload {
        if (fingerprint == null || fingerprint.length == 0) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "设备指纹不可为空");
        }
        fingerprint = fingerprint.clone();
    }

    @Override
    public byte[] fingerprint() {
        return fingerprint.clone();
    }

    public byte[] encode() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bos)) {
            out.writeInt(fingerprint.length);
            out.write(fingerprint);
            out.writeDouble(hardwareScore);
            out.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "NODE_REGISTER 编码失败", e);
        }
    }

    public static NodeRegisterPayload decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "NODE_REGISTER 负载为空");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int len = in.readInt();
            if (len <= 0 || len > 1024) {
                throw new VMException(VMException.Kind.INVALID_PAYLOAD, "指纹长度非法: " + len);
            }
            byte[] fp = new byte[len];
            in.readFully(fp);
            double hw = in.readDouble();
            return new NodeRegisterPayload(fp, hw);
        } catch (IOException e) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "NODE_REGISTER 解码失败", e);
        }
    }
}
