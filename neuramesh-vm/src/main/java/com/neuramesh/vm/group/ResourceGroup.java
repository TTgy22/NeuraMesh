package com.neuramesh.vm.group;

import com.neuramesh.vm.exception.VMException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 资源组：按地区动态分区的节点集合。
 *
 * <p>厂商可选购某个资源组（如 {@code north-china-qingdao}），系统在组内按权重分配任务。
 * 节点可随时加入/退出，组内成员实时变化。
 *
 * <p>字段：
 * <ul>
 *   <li>{@code groupId}：稳定标识，小写 + 连字符（如 {@code east-china-shanghai}）；</li>
 *   <li>{@code region}：人类可读地区名（如 "华东-上海"）；</li>
 *   <li>{@code minBenchmarkScore}：软性加入门槛，组内最低 DeviceBenchmark 分数；</li>
 *   <li>{@code requiredHttp2}：是否要求节点支持 HTTP/2.0；</li>
 *   <li>{@code nodeIds}：组内节点地址 hex 有序集合（去重、保持插入顺序，commit 时排序）。</li>
 * </ul>
 *
 * <p>本类可变（节点增删），由 {@link ResourceGroupState} 串行托管；{@link #copy()} 提供深拷贝供快照。
 */
public final class ResourceGroup {

    private final String groupId;
    private final String region;
    private final double minBenchmarkScore;
    private final boolean requiredHttp2;
    private final Set<String> nodeIds = new LinkedHashSet<>();

    public ResourceGroup(String groupId, String region, double minBenchmarkScore, boolean requiredHttp2) {
        if (groupId == null || groupId.isBlank()) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "groupId 不可为空");
        }
        if (region == null || region.isBlank()) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "region 不可为空");
        }
        if (minBenchmarkScore < 0) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "minBenchmarkScore 不可为负");
        }
        this.groupId = groupId;
        this.region = region;
        this.minBenchmarkScore = minBenchmarkScore;
        this.requiredHttp2 = requiredHttp2;
    }

    /** 深拷贝（快照用）。 */
    public ResourceGroup copy() {
        ResourceGroup c = new ResourceGroup(groupId, region, minBenchmarkScore, requiredHttp2);
        c.nodeIds.addAll(nodeIds);
        return c;
    }

    public boolean addNode(String nodeIdHex) {
        return nodeIds.add(nodeIdHex);
    }

    public boolean removeNode(String nodeIdHex) {
        return nodeIds.remove(nodeIdHex);
    }

    public boolean containsNode(String nodeIdHex) {
        return nodeIds.contains(nodeIdHex);
    }

    /** 组内节点地址 hex 列表（只读副本）。 */
    public List<String> nodeIds() {
        return Collections.unmodifiableList(new ArrayList<>(nodeIds));
    }

    /** 组内节点排序后的列表（确定性，用于 commit）。 */
    public List<String> sortedNodeIds() {
        List<String> out = new ArrayList<>(nodeIds);
        Collections.sort(out);
        return out;
    }

    public int nodeCount() {
        return nodeIds.size();
    }

    public String groupId() {
        return groupId;
    }

    public String region() {
        return region;
    }

    public double minBenchmarkScore() {
        return minBenchmarkScore;
    }

    public boolean requiredHttp2() {
        return requiredHttp2;
    }
}
