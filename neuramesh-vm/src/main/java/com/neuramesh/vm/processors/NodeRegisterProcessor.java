package com.neuramesh.vm.processors;

import com.neuramesh.core.CryptoUtils;
import com.neuramesh.core.Transaction;
import com.neuramesh.core.TxType;
import com.neuramesh.vm.TransactionProcessor;
import com.neuramesh.vm.exception.VMException;
import com.neuramesh.vm.group.GroupValidator;
import com.neuramesh.vm.group.ResourceGroup;
import com.neuramesh.vm.payload.NodeRegisterPayload;
import com.neuramesh.vm.state.GlobalState;
import com.neuramesh.vm.state.NodeState;

/**
 * NODE_REGISTER 处理器：解析设备指纹，校验唯一性，初始化 {@link NodeState}。
 *
 * <p>节点身份取 {@code tx.getFrom()}；同一指纹或同一节点不可重复注册。注册费当前为 0（债务）。
 *
 * <p>P5 修复（"无合格节点"）：
 * <ul>
 *   <li>注册即赋初始权重：硬件分立即生效（下限 1），{@code totalWeight = max(hw,1)*0.3 > 0}，
 *       新节点可即时参与组内结算；后续 WEIGHT_UPDATE（带见证）覆盖为完整四项分数；</li>
 *   <li>未指定资源组时兜底加入默认组 {@value #DEFAULT_GROUP_ID}（不存在则确定性自动创建，
 *       门槛 0、不要求 HTTP2，兜底加入永不失败），保证每个节点必属一组。</li>
 * </ul>
 */
public final class NodeRegisterProcessor implements TransactionProcessor {

    /** 默认兜底资源组 id：注册未指定分组时自动加入。 */
    public static final String DEFAULT_GROUP_ID = "general-purpose";
    /** 默认兜底资源组地区名。 */
    public static final String DEFAULT_GROUP_REGION = "通用-全局";

    private final GroupValidator groupValidator = new GroupValidator();

    @Override
    public void process(Transaction tx, GlobalState state) {
        NodeRegisterPayload p = NodeRegisterPayload.decode(tx.getPayload());
        byte[] nodeId = tx.getFrom();

        if (state.getNode(nodeId) != null) {
            throw new VMException(VMException.Kind.DUPLICATE_REGISTRATION, "节点已注册");
        }
        if (state.isFingerprintRegistered(p.fingerprint())) {
            throw new VMException(VMException.Kind.DUPLICATE_REGISTRATION, "设备指纹已被注册");
        }
        if (p.hardwareScore() < 0) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "硬件分数不可为负");
        }

        // 注册即赋初始权重：硬件分下限 1 → totalWeight > 0，避免组内结算"无合格节点"；
        // 完整四项分数随后由 WEIGHT_UPDATE（带见证）覆盖
        NodeState node = new NodeState(nodeId, p.fingerprint());
        node.setScores(Math.max(p.hardwareScore(), 1.0), 0, 0, 0);
        state.putNode(node);
        state.registerFingerprint(p.fingerprint());

        // P5：加入资源组（软性验证：性能门槛真实校验，HTTP2/GPS/IP 占位）；未指定时兜底默认组
        String groupId = p.resourceGroupId().isBlank() ? DEFAULT_GROUP_ID : p.resourceGroupId();
        if (p.resourceGroupId().isBlank()) {
            ensureDefaultGroup(state);
        }
        joinGroup(state, CryptoUtils.toHex(nodeId), groupId, p.hardwareScore(), tx.getTimestamp());
    }

    /** 默认组不存在则创建（作为交易处理的一部分，确定性，所有副本一致）。 */
    private static void ensureDefaultGroup(GlobalState state) {
        if (state.resourceGroups().getGroup(DEFAULT_GROUP_ID) == null) {
            state.resourceGroups().createGroup(
                    new ResourceGroup(DEFAULT_GROUP_ID, DEFAULT_GROUP_REGION, 0, false));
        }
    }

    private void joinGroup(GlobalState state, String nodeIdHex, String groupId,
                           double benchmarkScore, long joinedAt) {
        ResourceGroup group = state.resourceGroups().getGroup(groupId);
        if (group == null) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "资源组不存在: " + groupId);
        }
        // HTTP/2 支持探测占位（真实握手 TODO: P6），注册路径默认声明支持
        GroupValidator.Result result = groupValidator.validate(group, benchmarkScore, true);
        if (!result.passed()) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD,
                    "加入资源组失败: " + result.reason());
        }
        state.resourceGroups().addNodeToGroup(nodeIdHex, groupId, joinedAt, true);
    }

    @Override
    public TxType getType() {
        return TxType.NODE_REGISTER;
    }
}
