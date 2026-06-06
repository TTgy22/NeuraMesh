package com.neuramesh.api.service;

import com.neuramesh.api.dto.NodeStatusDTO;
import com.neuramesh.api.dto.ResourceGroupDTO;
import com.neuramesh.api.dto.TaskStatusDTO;
import com.neuramesh.core.CryptoUtils;
import com.neuramesh.core.Transaction;
import com.neuramesh.core.TxType;
import com.neuramesh.vm.group.GroupValidator;
import com.neuramesh.vm.group.ResourceGroup;
import com.neuramesh.vm.payload.TaskSettlePayload;
import com.neuramesh.vm.state.NodeState;
import com.neuramesh.vm.state.ResourceGroupState;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    private final ChainService chain;
    private final NodeService nodeService;
    private final GroupValidator validator = new GroupValidator();

    private final Map<String, KeyPair> vendorKeys = new ConcurrentHashMap<>();
    private final AtomicLong taskSeq = new AtomicLong(1);

    public ResourceGroupService(ChainService chain, NodeService nodeService) {
        this.chain = chain;
        this.nodeService = nodeService;
    }

    @PostConstruct
    void seed() {
        ResourceGroupState gs = chain.state().resourceGroups();
        createIfAbsent(gs, "north-china-qingdao", "华北-青岛", 50, false);
        createIfAbsent(gs, "east-china-shanghai", "华东-上海", 100, true);
        createIfAbsent(gs, "south-china-shenzhen", "华南-深圳", 80, false);
        LOG.info("资源组播种完成：{} 个组", gs.groupCount());
    }

    private void createIfAbsent(ResourceGroupState gs, String id, String region,
                                double minScore, boolean http2) {
        if (gs.getGroup(id) == null) {
            gs.createGroup(new ResourceGroup(id, region, minScore, http2));
        }
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
        gs.addNodeToGroup(hex, groupId, System.currentTimeMillis(), true);
        LOG.info("节点 {} 加入资源组 {}", hex, groupId);
        return toDto(g);
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
        return new ResourceGroupDTO(g.groupId(), g.region(), g.minBenchmarkScore(),
                g.requiredHttp2(), g.nodeCount(), totalWeight, avgLatency, onlineRate);
    }

    private static String strip(String hex) {
        return (hex.startsWith("0x") ? hex.substring(2) : hex).toLowerCase();
    }
}
