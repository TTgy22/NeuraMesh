package com.neuramesh.api.service;

import com.neuramesh.api.dto.EarningsPointDTO;
import com.neuramesh.api.dto.NodeStatusDTO;
import com.neuramesh.benchmark.BenchmarkResult;
import com.neuramesh.benchmark.DeviceBenchmark;
import com.neuramesh.benchmark.Fingerprint;
import com.neuramesh.core.CryptoUtils;
import com.neuramesh.core.Transaction;
import com.neuramesh.core.TxType;
import com.neuramesh.vm.Attestation;
import com.neuramesh.vm.payload.NodeRegisterPayload;
import com.neuramesh.vm.payload.WeightUpdatePayload;
import com.neuramesh.vm.state.NodeState;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 节点服务：封装设备 Benchmark + 状态机，提供注册、启停、状态与收益查询。
 *
 * <p>注册流程：跑 Benchmark → 生成指纹 → 生成密钥对（地址即 NodeID）→ 提交 NODE_REGISTER →
 * 再以 Benchmark 分数 + 验证者见证提交 WEIGHT_UPDATE 赋予权重。
 */
@Service
public class NodeService {

    private static final Logger LOG = LoggerFactory.getLogger(NodeService.class);
    private static final SecureRandom RND = new SecureRandom();

    private final ChainService chain;
    private final DeviceBenchmark benchmark = new DeviceBenchmark(1000);

    private final Map<String, KeyPair> nodeKeys = new ConcurrentHashMap<>();
    private final Map<String, Boolean> online = new ConcurrentHashMap<>();
    private final Map<String, String> deviceModels = new ConcurrentHashMap<>();

    public NodeService(ChainService chain) {
        this.chain = chain;
    }

    /**
     * 注册新节点（未指定资源组：链上兜底加入 general-purpose 默认组）。
     *
     * @param deviceModel 设备型号
     * @return 节点状态
     */
    public NodeStatusDTO register(String deviceModel) {
        return register(deviceModel, null);
    }

    /**
     * 注册新节点并赋予初始权重，返回节点状态。
     *
     * @param deviceModel     设备型号
     * @param resourceGroupId 目标资源组 id（null/空 → 链上兜底默认组 general-purpose）
     * @return 节点状态
     */
    public NodeStatusDTO register(String deviceModel, String resourceGroupId) {
        String model = (deviceModel == null || deviceModel.isBlank()) ? "generic-edge" : deviceModel;
        String groupId = resourceGroupId == null ? "" : resourceGroupId.trim();
        BenchmarkResult result = benchmark.run(model);
        byte[] salt = new byte[16];
        RND.nextBytes(salt);
        Fingerprint fp = Fingerprint.generate(result, salt);

        KeyPair kp = CryptoUtils.generateKeyPair();
        byte[] nodeId = CryptoUtils.toAddress(kp.getPublic());
        String nodeIdHex = CryptoUtils.toHex(nodeId);
        nodeKeys.put(nodeIdHex, kp);
        deviceModels.put(nodeIdHex, model);

        // 1) NODE_REGISTER（注册即得初始权重 hw*0.3，并加入资源组/兜底默认组）
        NodeRegisterPayload regPayload =
                new NodeRegisterPayload(fp.getHash(), normalizedScore(result), groupId);
        chain.applyTx(signTx(TxType.NODE_REGISTER, kp, nodeId, nodeId, 0, regPayload.encode()));

        // 2) WEIGHT_UPDATE（≥2 验证者一致见证，覆盖为完整四项分数）
        double hardware = normalizedScore(result);
        double claimed = hardware;
        // 质量/在线/带宽由跑分按比例派生（真实测量 TODO P6：任务校验/心跳累计/测速），
        // totalWeight = hw*0.3 + 0.9hw*0.4 + 0.95hw*0.2 + 0.8hw*0.1 = 0.93*hw —— 权重随设备性能变化
        double quality = hardware * 0.90;
        double uptime = hardware * 0.95;
        double bandwidth = hardware * 0.80;
        List<Attestation> atts = new ArrayList<>();
        atts.add(chain.attest(0, nodeId, claimed));
        atts.add(chain.attest(1, nodeId, claimed));
        WeightUpdatePayload wu = new WeightUpdatePayload(nodeId, hardware, quality, uptime, bandwidth, atts);
        chain.applyTx(signTx(TxType.WEIGHT_UPDATE, kp, nodeId, nodeId, 1, wu.encode()));

        online.put(nodeIdHex, true);
        LOG.info("节点注册成功 {} model={} group={}", nodeIdHex, model,
                groupId.isBlank() ? "general-purpose(兜底)" : groupId);
        return status(nodeIdHex);
    }

    public NodeStatusDTO start(String nodeIdHex) {
        online.put(strip(nodeIdHex), true);
        return status(nodeIdHex);
    }

    public NodeStatusDTO stop(String nodeIdHex) {
        online.put(strip(nodeIdHex), false);
        return status(nodeIdHex);
    }

    /**
     * 查询节点状态。
     *
     * @param nodeIdHex 节点地址 hex
     * @return 状态；节点不存在返回 null
     */
    public NodeStatusDTO status(String nodeIdHex) {
        String hex = strip(nodeIdHex);
        NodeState ns = chain.state().getNode(hexToBytes(hex));
        if (ns == null) {
            return null;
        }
        return new NodeStatusDTO("0x" + hex, online.getOrDefault(hex, false),
                deviceModels.getOrDefault(hex, "unknown"),
                ns.getHardwareScore(), ns.getQualityScore(), ns.getUptimeScore(),
                ns.getBandwidthScore(), ns.getTotalWeight(), ns.getTotalEarned(),
                level(ns.getTotalWeight()), CryptoUtils.toHex(ns.getFingerprint()));
    }

    /**
     * 收益曲线：将累计收益按天分布（真实结构，来自链上 totalEarned）。
     *
     * @param nodeIdHex 节点地址
     * @param days      天数
     * @return 收益点列表
     */
    public List<EarningsPointDTO> earnings(String nodeIdHex, int days) {
        String hex = strip(nodeIdHex);
        NodeState ns = chain.state().getNode(hexToBytes(hex));
        long total = ns == null ? 0 : ns.getTotalEarned();
        int n = Math.max(1, days);
        List<EarningsPointDTO> points = new ArrayList<>(n);
        long per = total / n;
        long remainder = total - per * n;
        for (int i = 0; i < n; i++) {
            long e = per + (i == n - 1 ? remainder : 0);
            points.add(new EarningsPointDTO(String.format("D-%d", n - i), e));
        }
        return points;
    }

    /**
     * 所有已注册节点的状态列表（用于控制台总览/硬件墙）。
     *
     * @return 节点状态列表
     */
    public List<NodeStatusDTO> allNodeStatuses() {
        List<NodeStatusDTO> out = new ArrayList<>();
        for (String hex : nodeKeys.keySet()) {
            NodeStatusDTO dto = status(hex);
            if (dto != null) {
                out.add(dto);
            }
        }
        return out;
    }

    /**
     * 在线且权重 &gt; 0 的可参与结算的节点地址列表。
     *
     * @return 节点地址字节列表
     */
    public List<byte[]> eligibleNodes() {
        List<byte[]> out = new ArrayList<>();
        for (Map.Entry<String, Boolean> e : online.entrySet()) {
            if (!Boolean.TRUE.equals(e.getValue())) {
                continue;
            }
            byte[] id = hexToBytes(e.getKey());
            NodeState ns = chain.state().getNode(id);
            if (ns != null && ns.getTotalWeight() > 0) {
                out.add(id);
            }
        }
        return out;
    }

    /** 节点等级（基于总权重）。 */
    static String level(double weight) {
        if (weight >= 500) {
            return "钻石";
        } else if (weight >= 300) {
            return "铂金";
        } else if (weight >= 150) {
            return "黄金";
        } else if (weight > 0) {
            return "白银";
        }
        return "青铜";
    }

    private static double normalizedScore(BenchmarkResult result) {
        // 将吞吐分数压缩到 0..1000 区间，避免极端值
        return Math.min(1000.0, result.score() / 1000.0);
    }

    private Transaction signTx(TxType type, KeyPair kp, byte[] from, byte[] to,
                               long nonce, byte[] payload) {
        Transaction tx = Transaction.create(type, from, to, nonce, payload,
                System.currentTimeMillis());
        return tx.withSignature(CryptoUtils.sign(tx.signingBytes(), kp.getPrivate()));
    }

    private static String strip(String hex) {
        return (hex.startsWith("0x") ? hex.substring(2) : hex).toLowerCase();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length() / 2;
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
