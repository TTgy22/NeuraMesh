package com.neuramesh.api.service;

import com.neuramesh.api.dto.NodeStatusDTO;
import com.neuramesh.api.dto.PurchaseReceiptDTO;
import com.neuramesh.api.dto.ResourceGroupDTO;
import com.neuramesh.api.dto.TaskStatusDTO;
import com.neuramesh.api.security.CryptoBox;
import com.neuramesh.api.security.UserPrincipal;
import com.neuramesh.core.CryptoUtils;
import com.neuramesh.core.Transaction;
import com.neuramesh.core.TxType;
import com.neuramesh.vm.group.GroupValidator;
import com.neuramesh.vm.group.ResourceGroup;
import com.neuramesh.vm.payload.TaskSettlePayload;
import com.neuramesh.vm.payload.TokenTransferPayload;
import com.neuramesh.vm.state.NodeState;
import com.neuramesh.vm.state.ResourceGroupState;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 资源组服务：封装 {@link ResourceGroupState} 查询，提供节点加入校验与按组任务分配（结算）。
 *
 * <p>启动时播种若干默认地区资源组（演示用）。节点加入经 {@link GroupValidator} 软性验证后直接写入
 * 资源组状态（与 {@code ChainService.fund} 同属演示态直写，未走区块）。任务分配 {@link #allocateTask}
 * 构造绑定 {@code resourceGroupId} 的 TASK_SETTLE 交易，由状态机在组内按权重自动分配。
 */
@Service
public class ResourceGroupService {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceGroupService.class);
    private static final long INITIAL_FUNDING = 1_000_000L;
    /** 平台主密钥：加密安全组私钥、签发成员凭证（赛事 Demo 固定值；TODO P6 改 KMS）。 */
    private static final String PLATFORM_SECRET = "neuramesh-platform-master-secret";

    private final ChainService chain;
    private final NodeService nodeService;
    private final GroupValidator validator = new GroupValidator();

    private final Map<String, KeyPair> vendorKeys = new ConcurrentHashMap<>();
    private final AtomicLong taskSeq = new AtomicLong(1);

    /** 安全组私钥明文缓存（groupId → privKeyHex），购买时交付。 */
    private final Map<String, String> groupPrivKeys = new ConcurrentHashMap<>();
    /** 平台金库密钥对（购买扣款的收款方）。 */
    private final KeyPair treasuryKey = CryptoUtils.generateKeyPair();
    /** 平台签发成员凭证的密钥对。 */
    private final KeyPair platformKey = CryptoUtils.generateKeyPair();
    /** 用户购买记录（userId → 购买列表）。 */
    private final Map<String, List<Purchase>> purchases = new ConcurrentHashMap<>();
    /** 资源组规格元数据（groupId → Spec），仅 API 层展示用，不入链。 */
    private final Map<String, Spec> specs = new ConcurrentHashMap<>();

    /** 一笔资源组购买（可变到期时间，支持续费叠加）。 */
    private static final class Purchase {
        final String groupId;
        int hours;
        long totalCost;
        final long purchasedAt;
        long expiresAt;
        String settleTxId;
        final String groupPrivKey;

        Purchase(String groupId, int hours, long totalCost, long purchasedAt, long expiresAt,
                 String settleTxId, String groupPrivKey) {
            this.groupId = groupId;
            this.hours = hours;
            this.totalCost = totalCost;
            this.purchasedAt = purchasedAt;
            this.expiresAt = expiresAt;
            this.settleTxId = settleTxId;
            this.groupPrivKey = groupPrivKey;
        }
    }

    private record Spec(String category, int reliabilityPct, int multiNodePct, List<String> tags) {
    }

    public ResourceGroupService(ChainService chain, NodeService nodeService) {
        this.chain = chain;
        this.nodeService = nodeService;
    }

    @PostConstruct
    void seed() {
        ResourceGroupState gs = chain.state().resourceGroups();
        // 阿里云风格规格族：通用型(均衡) / 计算型(高算力) / 高可靠型(冗余) / 存储型 / 网络增强型
        createIfAbsent(gs, "north-china-qingdao", "华北-青岛", 50, false, 20_000L,
                new Spec("通用型 g7", 50, 50, List.of("均衡", "SLA99.5")));
        createIfAbsent(gs, "east-china-shanghai", "华东-上海", 100, true, 50_000L,
                new Spec("计算型 c7", 70, 30, List.of("GPU", "高算力", "HTTP/2", "SLA99.9")));
        createIfAbsent(gs, "south-china-shenzhen", "华南-深圳", 80, false, 35_000L,
                new Spec("高可靠型 r7", 40, 60, List.of("多节点冗余", "容灾", "SLA99.95")));
        createIfAbsent(gs, "north-china-beijing", "华北-北京", 120, true, 60_000L,
                new Spec("计算型 c7", 80, 20, List.of("GPU", "低延迟", "HTTP/2", "SLA99.9")));
        createIfAbsent(gs, "east-china-hangzhou", "华东-杭州", 60, false, 30_000L,
                new Spec("存储型 d3", 30, 70, List.of("大带宽", "高吞吐", "SLA99.5")));
        createIfAbsent(gs, "southwest-china-chengdu", "西南-成都", 40, false, 18_000L,
                new Spec("通用型 g7", 55, 45, List.of("性价比", "SLA99.0")));
        LOG.info("资源组播种完成：{} 个组", gs.groupCount());
    }

    private void createIfAbsent(ResourceGroupState gs, String id, String region,
                                double minScore, boolean http2, long pricePerHour, Spec spec) {
        specs.put(id, spec);
        if (gs.getGroup(id) != null) {
            return;
        }
        // 为资源组生成安全组密钥对：公钥公开，私钥明文缓存 + 加密存链上
        KeyPair groupKp = CryptoUtils.generateKeyPair();
        String pubHex = CryptoUtils.toHex(groupKp.getPublic().getEncoded());
        String privHex = CryptoUtils.toHex(groupKp.getPrivate().getEncoded());
        groupPrivKeys.put(id, privHex);
        String encryptedPriv = CryptoBox.encrypt(privHex, PLATFORM_SECRET);
        gs.createGroup(new ResourceGroup(id, region, minScore, http2, pubHex, encryptedPriv, pricePerHour));
    }

    /** 所有资源组视图。 */
    public List<ResourceGroupDTO> list() {
        List<ResourceGroupDTO> out = new ArrayList<>();
        for (ResourceGroup g : chain.state().resourceGroups().allGroups()) {
            out.add(toDto(g));
        }
        out.sort((a, b) -> a.groupId().compareTo(b.groupId()));
        return out;
    }

    /** 单个资源组视图（不存在返回 null）。 */
    public ResourceGroupDTO detail(String groupId) {
        ResourceGroup g = chain.state().resourceGroups().getGroup(groupId);
        return g == null ? null : toDto(g);
    }

    /** 组内节点状态列表（不存在返回 null）。 */
    public List<NodeStatusDTO> nodesOf(String groupId) {
        ResourceGroup g = chain.state().resourceGroups().getGroup(groupId);
        if (g == null) {
            return null;
        }
        List<NodeStatusDTO> out = new ArrayList<>();
        for (String hex : g.sortedNodeIds()) {
            NodeStatusDTO dto = nodeService.status(hex);
            if (dto != null) {
                out.add(dto);
            }
        }
        return out;
    }

    /**
     * 节点申请加入资源组：软性验证通过后写入组成员关系。
     *
     * @param groupId   资源组 id
     * @param nodeIdHex 节点地址 hex（可带 0x）
     * @return 加入结果（含组视图）；组或节点不存在抛出 {@link IllegalArgumentException}
     */
    public synchronized ResourceGroupDTO join(String groupId, String nodeIdHex) {
        ResourceGroupState gs = chain.state().resourceGroups();
        ResourceGroup g = gs.getGroup(groupId);
        if (g == null) {
            throw new IllegalArgumentException("资源组不存在: " + groupId);
        }
        String hex = strip(nodeIdHex);
        NodeState ns = chain.state().getNode(CryptoUtils.fromHex(hex));
        if (ns == null) {
            throw new IllegalArgumentException("节点不存在: " + nodeIdHex);
        }
        // benchmark 分数用硬件分；HTTP2 支持占位为 true（真实握手 TODO P6）
        GroupValidator.Result r = validator.validate(g, ns.getHardwareScore(), true);
        if (!r.passed()) {
            throw new IllegalArgumentException("加入资源组失败: " + r.reason());
        }
        // 平台签发成员凭证：对 nodeId|groupId 的平台私钥签名
        String cert = issueMembershipCertificate(hex, groupId);
        gs.addNodeToGroup(hex, groupId, System.currentTimeMillis(), true, cert);
        LOG.info("节点 {} 加入资源组 {}（已签发成员凭证）", hex, groupId);
        return toDto(g);
    }

    /** 平台对 {@code nodeId|groupId} 签发成员凭证（hex 签名）。 */
    private String issueMembershipCertificate(String nodeHex, String groupId) {
        byte[] msg = (nodeHex + "|" + groupId).getBytes(StandardCharsets.UTF_8);
        return CryptoUtils.toHex(CryptoUtils.sign(msg, platformKey.getPrivate()));
    }

    /**
     * 在指定资源组内分配并结算一个任务（按组内节点权重自动分配）。
     *
     * @param groupId  资源组 id
     * @param vendorId 厂商标签
     * @param taskType 任务类型
     * @param budget   预算
     * @return 任务状态
     */
    public synchronized TaskStatusDTO allocateTask(String groupId, String vendorId,
                                                   String taskType, long budget) {
        ResourceGroup g = chain.state().resourceGroups().getGroup(groupId);
        String taskId = "gtask-" + taskSeq.getAndIncrement();
        String type = (taskType == null || taskType.isBlank()) ? "image-classification" : taskType;
        long fee = budget > 0 ? budget : 30_000L;
        if (g == null) {
            return new TaskStatusDTO(taskId, type, "FAILED", fee, null, List.of(), null);
        }

        List<String> eligible = new ArrayList<>();
        for (String hex : g.sortedNodeIds()) {
            NodeState ns = chain.state().getNode(CryptoUtils.fromHex(hex));
            if (ns != null && ns.getTotalWeight() > 0) {
                eligible.add("0x" + hex);
            }
        }
        if (eligible.isEmpty()) {
            LOG.warn("资源组 {} 内无合格节点，任务 {} 失败", groupId, taskId);
            return new TaskStatusDTO(taskId, type, "FAILED", fee, null, List.of(), null);
        }

        String label = (vendorId == null || vendorId.isBlank()) ? "vendor-1" : vendorId;
        KeyPair kp = vendorKeys.computeIfAbsent(label, k -> CryptoUtils.generateKeyPair());
        byte[] vendorAddr = CryptoUtils.toAddress(kp.getPublic());
        if (chain.balanceOf(vendorAddr) < fee) {
            chain.fund(vendorAddr, INITIAL_FUNDING);
        }

        // 空分配 + groupId：状态机在组内按权重自动分配
        TaskSettlePayload payload = new TaskSettlePayload(
                taskId.getBytes(StandardCharsets.UTF_8), fee, List.of(), groupId);
        long nonce = chain.nonceOf(vendorAddr);
        Transaction tx = Transaction.create(TxType.TASK_SETTLE, vendorAddr, vendorAddr, nonce,
                payload.encode(), System.currentTimeMillis());
        tx = tx.withSignature(CryptoUtils.sign(tx.signingBytes(), kp.getPrivate()));
        chain.applyTx(tx);

        LOG.info("资源组 {} 任务 {} 结算完成，节点 {} 个，预算 {}",
                groupId, taskId, eligible.size(), fee);
        return new TaskStatusDTO(taskId, type, "SETTLED", fee,
                "0x" + CryptoUtils.toHex(tx.getTxId()), eligible,
                "https://placeholder.co/400x300?text=" + taskId);
    }

    /**
     * 厂商购买资源组：校验余额 → 链上扣款（TOKEN_TRANSFER 到金库）→ 发放安全组私钥 + 记录凭证。
     *
     * @param groupId   资源组 id
     * @param principal 当前用户（含链上地址）
     * @param hours     购买时长（小时）
     * @return 购买凭证（含明文 groupPrivateKey）
     */
    public synchronized PurchaseReceiptDTO buy(String groupId, UserPrincipal principal, int hours) {
        ResourceGroup g = chain.state().resourceGroups().getGroup(groupId);
        if (g == null) {
            throw new IllegalArgumentException("资源组不存在: " + groupId);
        }
        if (hours <= 0) {
            throw new IllegalArgumentException("购买时长必须为正");
        }
        long totalCost = Math.multiplyExact(g.pricePerHour(), (long) hours);
        byte[] payer = CryptoUtils.fromHex(strip(principal.address()));
        if (chain.balanceOf(payer) < totalCost) {
            throw new IllegalArgumentException("余额不足：需要 " + totalCost
                    + "，当前 " + chain.balanceOf(payer));
        }

        // 链上扣款：TOKEN_TRANSFER 用户 → 金库（StateMachine 校验余额并守恒）
        byte[] treasury = CryptoUtils.toAddress(treasuryKey.getPublic());
        long nonce = chain.nonceOf(payer);
        Transaction tx = Transaction.create(TxType.TOKEN_TRANSFER, payer, treasury, nonce,
                new TokenTransferPayload(totalCost).encode(), System.currentTimeMillis());
        chain.applyTx(tx);

        long now = System.currentTimeMillis();
        long expiresAt = now + (long) hours * 3600_000L;
        String settleTxId = "0x" + CryptoUtils.toHex(tx.getTxId());
        String groupPrivKey = groupPrivKeys.getOrDefault(groupId, "");
        purchases.computeIfAbsent(principal.userId(), k -> new CopyOnWriteArrayList<>())
                .add(new Purchase(groupId, hours, totalCost, now, expiresAt, settleTxId, groupPrivKey));

        LOG.info("用户 {} 购买资源组 {} {}h 花费 {}", principal.username(), groupId, hours, totalCost);
        // TODO: P6 groupPrivateKey 加密交付（当前明文，赛事简化）
        return new PurchaseReceiptDTO(groupId, g.region(), hours, totalCost, expiresAt,
                settleTxId, groupPrivKey, chain.balanceOf(payer));
    }

    /**
     * 续费：在已有购买上<strong>叠加</strong>时长（不新增列表条目）。无既有购买则等同新购。
     *
     * @param groupId   资源组 id
     * @param principal 当前用户
     * @param hours     续费时长
     * @return 购买凭证（含叠加后的累计时长/费用与新到期时间）
     */
    public synchronized PurchaseReceiptDTO renew(String groupId, UserPrincipal principal, int hours) {
        ResourceGroup g = chain.state().resourceGroups().getGroup(groupId);
        if (g == null) {
            throw new IllegalArgumentException("资源组不存在: " + groupId);
        }
        Purchase existing = latestPurchase(principal.userId(), groupId);
        if (existing == null) {
            return buy(groupId, principal, hours);
        }
        if (hours <= 0) {
            throw new IllegalArgumentException("续费时长必须为正");
        }
        long addCost = Math.multiplyExact(g.pricePerHour(), (long) hours);
        byte[] payer = CryptoUtils.fromHex(strip(principal.address()));
        if (chain.balanceOf(payer) < addCost) {
            throw new IllegalArgumentException("余额不足：需要 " + addCost + "，当前 " + chain.balanceOf(payer));
        }
        byte[] treasury = CryptoUtils.toAddress(treasuryKey.getPublic());
        long nonce = chain.nonceOf(payer);
        Transaction tx = Transaction.create(TxType.TOKEN_TRANSFER, payer, treasury, nonce,
                new TokenTransferPayload(addCost).encode(), System.currentTimeMillis());
        chain.applyTx(tx);

        // 叠加：从 max(now, 原到期) 起再加 hours
        long now = System.currentTimeMillis();
        long base = Math.max(now, existing.expiresAt);
        existing.expiresAt = base + (long) hours * 3600_000L;
        existing.hours += hours;
        existing.totalCost += addCost;
        existing.settleTxId = "0x" + CryptoUtils.toHex(tx.getTxId());

        LOG.info("用户 {} 续费资源组 {} +{}h（累计 {}h，到期 {}）", principal.username(), groupId,
                hours, existing.hours, existing.expiresAt);
        return new PurchaseReceiptDTO(groupId, g.region(), existing.hours, existing.totalCost,
                existing.expiresAt, existing.settleTxId, existing.groupPrivKey, chain.balanceOf(payer));
    }

    private Purchase latestPurchase(String userId, String groupId) {
        Purchase found = null;
        for (Purchase p : purchases.getOrDefault(userId, List.of())) {
            if (p.groupId.equals(groupId) && (found == null || p.purchasedAt > found.purchasedAt)) {
                found = p;
            }
        }
        return found;
    }

    /**
     * 当前用户已购资源组列表。
     *
     * @param userId 用户 id
     * @return 购买记录视图列表
     */
    public List<Map<String, Object>> myGroups(String userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Purchase p : purchases.getOrDefault(userId, List.of())) {
            ResourceGroup g = chain.state().resourceGroups().getGroup(p.groupId);
            Spec spec = specs.get(p.groupId);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("groupId", p.groupId);
            m.put("region", g == null ? "" : g.region());
            m.put("category", spec == null ? "" : spec.category());
            m.put("hours", p.hours);
            m.put("totalCost", p.totalCost);
            m.put("purchasedAt", p.purchasedAt);
            m.put("expiresAt", p.expiresAt);
            m.put("remainingMs", Math.max(0, p.expiresAt - now));
            m.put("active", p.expiresAt > now);
            m.put("settleTxId", p.settleTxId);
            m.put("nodeCount", g == null ? 0 : g.nodeCount());
            m.put("groupPublicKey", g == null ? "" : g.groupPublicKey());
            m.put("groupPrivateKey", p.groupPrivKey);
            out.add(m);
        }
        return out;
    }

    private ResourceGroupDTO toDto(ResourceGroup g) {
        double totalWeight = 0;
        int online = 0;
        int count = 0;
        for (String hex : g.nodeIds()) {
            NodeState ns = chain.state().getNode(CryptoUtils.fromHex(hex));
            if (ns == null) {
                continue;
            }
            count++;
            totalWeight += ns.getTotalWeight();
            NodeStatusDTO st = nodeService.status(hex);
            if (st != null && st.online()) {
                online++;
            }
        }
        double onlineRate = count == 0 ? 0.0 : (double) online / count;
        // 平均延迟演示派生：权重越高视为越优，10~60ms 区间（真实测量 TODO P6）
        double avgLatency = count == 0 ? 0.0
                : Math.max(10.0, 60.0 - Math.min(50.0, totalWeight / Math.max(1, count) / 20.0));
        Spec spec = specs.getOrDefault(g.groupId(),
                new Spec("通用型 g7", 50, 50, List.of()));
        return new ResourceGroupDTO(g.groupId(), g.region(), g.minBenchmarkScore(),
                g.requiredHttp2(), g.nodeCount(), totalWeight, avgLatency, onlineRate,
                g.pricePerHour(), g.groupPublicKey(), spec.category(), spec.reliabilityPct(),
                spec.multiNodePct(), spec.tags());
    }

    private static String strip(String hex) {
        return (hex.startsWith("0x") ? hex.substring(2) : hex).toLowerCase();
    }
}
