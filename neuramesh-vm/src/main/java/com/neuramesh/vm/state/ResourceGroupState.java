package com.neuramesh.vm.state;

import com.neuramesh.core.ByteUtils;
import com.neuramesh.vm.exception.VMException;
import com.neuramesh.vm.group.GroupMembership;
import com.neuramesh.vm.group.ResourceGroup;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 资源组状态：管理所有 {@link ResourceGroup} 与节点的 {@link GroupMembership} 映射。
 *
 * <p>作为 {@link GlobalState} 的子状态，参与全局 Merkle Root 计算（{@link #commitLeaves()}）、
 * 快照与回滚（{@link #copy()} / {@link #restoreFrom(ResourceGroupState)}）。
 *
 * <p>资源组动态：节点可随时加入/退出，{@link #addNodeToGroup} 会将节点从旧组迁出后加入新组，
 * 保证一个节点同一时刻只属于一个组。
 *
 * <p>并发：底层 {@link ConcurrentHashMap}；写操作由状态机串行化（BFT 顺序）。
 */
public final class ResourceGroupState {

    private final ConcurrentMap<String, ResourceGroup> groups = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, GroupMembership> memberships = new ConcurrentHashMap<>();

    /**
     * 创建资源组（groupId 已存在则抛出）。
     *
     * @param group 资源组
     */
    public void createGroup(ResourceGroup group) {
        if (groups.containsKey(group.groupId())) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "资源组已存在: " + group.groupId());
        }
        groups.put(group.groupId(), group);
    }

    public ResourceGroup getGroup(String groupId) {
        return groups.get(groupId);
    }

    /**
     * 按地区名查询资源组（返回首个匹配，找不到返回 null）。
     *
     * @param region 地区名
     * @return 资源组或 null
     */
    public ResourceGroup getGroupByRegion(String region) {
        for (ResourceGroup g : groups.values()) {
            if (g.region().equals(region)) {
                return g;
            }
        }
        return null;
    }

    /** 所有资源组（只读副本）。 */
    public Collection<ResourceGroup> allGroups() {
        return Collections.unmodifiableCollection(new ArrayList<>(groups.values()));
    }

    public int groupCount() {
        return groups.size();
    }

    /**
     * 将节点加入资源组：若节点已属其他组则先迁出，再加入目标组并登记成员关系。
     *
     * @param nodeIdHex 节点地址 hex
     * @param groupId   目标组 id
     * @param joinedAt  加入时间戳
     * @param verified  软性验证是否通过
     */
    public void addNodeToGroup(String nodeIdHex, String groupId, long joinedAt, boolean verified) {
        addNodeToGroup(nodeIdHex, groupId, joinedAt, verified, "");
    }

    /**
     * 将节点加入资源组（带成员凭证）：若节点已属其他组则先迁出。
     *
     * @param nodeIdHex             节点地址 hex
     * @param groupId               目标组 id
     * @param joinedAt              加入时间戳
     * @param verified              软性验证是否通过
     * @param membershipCertificate 平台成员凭证（hex，可空）
     */
    public void addNodeToGroup(String nodeIdHex, String groupId, long joinedAt, boolean verified,
                               String membershipCertificate) {
        ResourceGroup target = groups.get(groupId);
        if (target == null) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "资源组不存在: " + groupId);
        }
        GroupMembership existing = memberships.get(nodeIdHex);
        if (existing != null && !existing.groupId().equals(groupId)) {
            ResourceGroup old = groups.get(existing.groupId());
            if (old != null) {
                old.removeNode(nodeIdHex);
            }
        }
        target.addNode(nodeIdHex);
        memberships.put(nodeIdHex,
                new GroupMembership(nodeIdHex, groupId, joinedAt, verified, membershipCertificate));
    }

    /**
     * 节点退出其所属资源组。
     *
     * @param nodeIdHex 节点地址 hex
     * @return 是否确有退出动作
     */
    public boolean removeNodeFromGroup(String nodeIdHex) {
        GroupMembership m = memberships.remove(nodeIdHex);
        if (m == null) {
            return false;
        }
        ResourceGroup g = groups.get(m.groupId());
        if (g != null) {
            g.removeNode(nodeIdHex);
        }
        return true;
    }

    public GroupMembership membershipOf(String nodeIdHex) {
        return memberships.get(nodeIdHex);
    }

    /**
     * Merkle 叶子（确定性：按键排序），供 {@link GlobalState#commit()} 拼接。
     *
     * @return 叶子字节列表
     */
    public List<byte[]> commitLeaves() {
        List<byte[]> leaves = new ArrayList<>();
        Map<String, ResourceGroup> sortedGroups = new TreeMap<>(groups);
        for (Map.Entry<String, ResourceGroup> e : sortedGroups.entrySet()) {
            ResourceGroup g = e.getValue();
            String members = String.join(",", g.sortedNodeIds());
            leaves.add(ByteUtils.concat(
                    ("G:" + g.groupId() + "|" + g.region() + "|" + g.groupPublicKey())
                            .getBytes(StandardCharsets.UTF_8),
                    ByteUtils.longToBytes(Double.doubleToLongBits(g.minBenchmarkScore())),
                    ByteUtils.longToBytes(g.pricePerHour()),
                    new byte[] {(byte) (g.requiredHttp2() ? 1 : 0)},
                    members.getBytes(StandardCharsets.UTF_8)));
        }
        Map<String, GroupMembership> sortedMembers = new TreeMap<>(memberships);
        for (Map.Entry<String, GroupMembership> e : sortedMembers.entrySet()) {
            GroupMembership m = e.getValue();
            leaves.add(ByteUtils.concat(
                    ("M:" + m.nodeIdHex() + "|" + m.groupId() + "|" + m.membershipCertificate())
                            .getBytes(StandardCharsets.UTF_8),
                    ByteUtils.longToBytes(m.joinedAt()),
                    new byte[] {(byte) (m.verified() ? 1 : 0)}));
        }
        return leaves;
    }

    /** 深拷贝（快照）。 */
    public ResourceGroupState copy() {
        ResourceGroupState s = new ResourceGroupState();
        for (Map.Entry<String, ResourceGroup> e : groups.entrySet()) {
            s.groups.put(e.getKey(), e.getValue().copy());
        }
        for (Map.Entry<String, GroupMembership> e : memberships.entrySet()) {
            s.memberships.put(e.getKey(), e.getValue().copy());
        }
        return s;
    }

    /**
     * 用快照覆盖当前状态（回滚）。
     *
     * @param snapshot 先前快照
     */
    public void restoreFrom(ResourceGroupState snapshot) {
        groups.clear();
        memberships.clear();
        for (Map.Entry<String, ResourceGroup> e : snapshot.groups.entrySet()) {
            groups.put(e.getKey(), e.getValue().copy());
        }
        for (Map.Entry<String, GroupMembership> e : snapshot.memberships.entrySet()) {
            memberships.put(e.getKey(), e.getValue().copy());
        }
    }
}
