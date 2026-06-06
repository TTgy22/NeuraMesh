package com.neuramesh.vm;

import static com.neuramesh.vm.TestVmSupport.addr;
import static com.neuramesh.vm.TestVmSupport.tx;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neuramesh.core.CryptoUtils;
import com.neuramesh.core.TxType;
import com.neuramesh.vm.exception.VMException;
import com.neuramesh.vm.group.GroupMembership;
import com.neuramesh.vm.group.GroupValidator;
import com.neuramesh.vm.group.ResourceGroup;
import com.neuramesh.vm.payload.NodeRegisterPayload;
import com.neuramesh.vm.payload.TaskSettlePayload;
import com.neuramesh.vm.state.GlobalState;
import com.neuramesh.vm.state.NodeState;
import com.neuramesh.vm.state.ResourceGroupState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P5 资源组架构测试：组管理、软性验证、按组分配结算、快照回滚。
 */
class ResourceGroupTest {

    private StateMachine sm() {
        return StateMachine.standard(TestVmSupport.validators(4).set());
    }

    private static ResourceGroup group(String id, String region, double minScore, boolean http2) {
        return new ResourceGroup(id, region, minScore, http2);
    }

    @Test
    @DisplayName("ResourceGroupState：创建组、按地区查询、节点加入与组间迁移")
    void groupState_createAndMigrate() {
        ResourceGroupState gs = new ResourceGroupState();
        gs.createGroup(group("north-china-qingdao", "华北-青岛", 100, false));
        gs.createGroup(group("east-china-shanghai", "华东-上海", 200, true));

        assertThat(gs.groupCount()).isEqualTo(2);
        assertThat(gs.getGroupByRegion("华北-青岛").groupId()).isEqualTo("north-china-qingdao");
        assertThat(gs.getGroupByRegion("不存在")).isNull();

        String node = CryptoUtils.toHex(addr(1));
        gs.addNodeToGroup(node, "north-china-qingdao", 1000L, true);
        assertThat(gs.getGroup("north-china-qingdao").containsNode(node)).isTrue();
        GroupMembership m = gs.membershipOf(node);
        assertThat(m.groupId()).isEqualTo("north-china-qingdao");
        assertThat(m.verified()).isTrue();

        // 迁移到另一组：旧组应移除
        gs.addNodeToGroup(node, "east-china-shanghai", 2000L, true);
        assertThat(gs.getGroup("north-china-qingdao").containsNode(node)).isFalse();
        assertThat(gs.getGroup("east-china-shanghai").containsNode(node)).isTrue();
        assertThat(gs.membershipOf(node).groupId()).isEqualTo("east-china-shanghai");

        // 退出
        assertThat(gs.removeNodeFromGroup(node)).isTrue();
        assertThat(gs.membershipOf(node)).isNull();
        assertThat(gs.getGroup("east-china-shanghai").containsNode(node)).isFalse();
    }

    @Test
    @DisplayName("创建重复 groupId → 抛出")
    void groupState_duplicateGroup() {
        ResourceGroupState gs = new ResourceGroupState();
        gs.createGroup(group("g1", "区域1", 0, false));
        assertThatThrownBy(() -> gs.createGroup(group("g1", "区域1", 0, false)))
                .isInstanceOf(VMException.class);
    }

    @Test
    @DisplayName("GroupValidator：分数达标通过，低于门槛或缺 HTTP2 失败")
    void validator_softRules() {
        GroupValidator v = new GroupValidator();
        ResourceGroup g = group("g", "区域", 500, true);

        assertThat(v.validate(g, 600, true).passed()).isTrue();
        assertThat(v.validate(g, 400, true).passed()).isFalse();
        assertThat(v.validate(g, 600, false).passed()).isFalse();

        // 不要求 HTTP2 时，缺失也通过
        ResourceGroup g2 = group("g2", "区域2", 100, false);
        assertThat(v.validate(g2, 100, false).passed()).isTrue();
    }

    @Test
    @DisplayName("NODE_REGISTER 指定资源组：达标加入，成员关系登记")
    void register_joinGroup() {
        StateMachine sm = sm();
        GlobalState state = new GlobalState();
        state.resourceGroups().createGroup(group("north-china-qingdao", "华北-青岛", 100, false));

        byte[] node = addr(5);
        NodeRegisterPayload p = new NodeRegisterPayload(fp(5), 300, "north-china-qingdao");
        sm.apply(tx(TxType.NODE_REGISTER, node, node, 0, p.encode()), state);

        assertThat(state.getNode(node)).isNotNull();
        GroupMembership m = state.resourceGroups().membershipOf(CryptoUtils.toHex(node));
        assertThat(m).isNotNull();
        assertThat(m.groupId()).isEqualTo("north-china-qingdao");
    }

    @Test
    @DisplayName("NODE_REGISTER 分数低于组门槛 → 失败回滚，节点未注册")
    void register_joinGroup_belowThreshold_rollback() {
        StateMachine sm = sm();
        GlobalState state = new GlobalState();
        state.resourceGroups().createGroup(group("hi-end", "高端组", 800, false));

        byte[] node = addr(6);
        NodeRegisterPayload p = new NodeRegisterPayload(fp(6), 300, "hi-end");
        assertThatThrownBy(() -> sm.apply(tx(TxType.NODE_REGISTER, node, node, 0, p.encode()), state))
                .isInstanceOf(VMException.class);

        // 回滚：节点与组成员都不存在
        assertThat(state.getNode(node)).isNull();
        assertThat(state.resourceGroups().getGroup("hi-end").nodeCount()).isZero();
        assertThat(state.resourceGroups().membershipOf(CryptoUtils.toHex(node))).isNull();
    }

    @Test
    @DisplayName("TASK_SETTLE 按资源组：空分配 + groupId → 组内按权重分配")
    void settle_byResourceGroup() {
        StateMachine sm = sm();
        GlobalState state = new GlobalState();
        state.resourceGroups().createGroup(group("g", "区域", 0, false));

        // 组内两个节点：权重 100 与 300（1:3）
        byte[] n1 = addr(2);
        byte[] n2 = addr(3);
        putWeightedNode(state, n1, 100);
        putWeightedNode(state, n2, 300);
        state.resourceGroups().addNodeToGroup(CryptoUtils.toHex(n1), "g", 1L, true);
        state.resourceGroups().addNodeToGroup(CryptoUtils.toHex(n2), "g", 1L, true);

        byte[] vendor = addr(1);
        state.credit(vendor, 400);
        TaskSettlePayload p = new TaskSettlePayload(
                "task-grp".getBytes(), 400, java.util.List.of(), "g");
        sm.apply(tx(TxType.TASK_SETTLE, vendor, vendor, 0, p.encode()), state);

        // 100:300 → 100 : 300
        assertThat(state.getAccount(n1).getBalance()).isEqualTo(100);
        assertThat(state.getAccount(n2).getBalance()).isEqualTo(300);
        assertThat(state.getAccount(vendor).getBalance()).isZero();
        assertThat(state.totalBalance()).isEqualTo(400);
    }

    @Test
    @DisplayName("TASK_SETTLE 指定不存在的资源组 → 失败回滚")
    void settle_unknownGroup_rollback() {
        StateMachine sm = sm();
        GlobalState state = new GlobalState();
        byte[] vendor = addr(1);
        state.credit(vendor, 100);
        TaskSettlePayload p = new TaskSettlePayload(
                "t".getBytes(), 100, java.util.List.of(), "missing");
        assertThatThrownBy(() -> sm.apply(tx(TxType.TASK_SETTLE, vendor, vendor, 0, p.encode()), state))
                .isInstanceOf(VMException.class);
        assertThat(state.getAccount(vendor).getBalance()).isEqualTo(100);
    }

    @Test
    @DisplayName("资源组纳入 state root：组变化导致 commit root 改变")
    void resourceGroup_affectsStateRoot() {
        GlobalState state = new GlobalState();
        byte[] before = state.commit();
        state.resourceGroups().createGroup(group("g", "区域", 0, false));
        byte[] after = state.commit();
        assertThat(after).isNotEqualTo(before);
    }

    private static byte[] fp(int seed) {
        byte[] f = new byte[32];
        for (int i = 0; i < f.length; i++) {
            f[i] = (byte) (seed * 13 + i);
        }
        return f;
    }

    private static void putWeightedNode(GlobalState state, byte[] nodeId, double weight) {
        NodeState ns = new NodeState(nodeId, fp((int) weight + 1));
        // 四项分数相等时 totalWeight = weight*(0.3+0.4+0.2+0.1) = weight
        ns.setScores(weight, weight, weight, weight);
        state.putNode(ns);
    }
}
