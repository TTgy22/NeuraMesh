package com.neuramesh.consensus.bft;

import com.neuramesh.consensus.exception.ConsensusException;
import com.neuramesh.core.CryptoUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 验证者集合（不可变）。
 *
 * <p>法定人数（Quorum）= {@code floor(2n/3) + 1}（拜占庭容错 3f+1 → 需 2f+1 票）。
 * 例：8 个验证者 → 6 票；4 个验证者 → 3 票。
 *
 * <p>验证者集初始固定，动态加入/退出留待 P3。
 */
public final class ValidatorSet {

    private final List<Validator> validators;
    private final Map<String, Validator> byId;
    private final long totalWeight;

    public ValidatorSet(List<Validator> validators) {
        if (validators == null || validators.isEmpty()) {
            throw new ConsensusException("验证者集不可为空");
        }
        List<Validator> copy = new ArrayList<>(validators);
        Map<String, Validator> map = new LinkedHashMap<>();
        long weightSum = 0;
        for (Validator v : copy) {
            if (map.put(v.getNodeIdHex(), v) != null) {
                throw new ConsensusException("验证者 nodeId 重复: " + v.getNodeIdHex());
            }
            weightSum += v.getWeight();
        }
        this.validators = Collections.unmodifiableList(copy);
        this.byId = Collections.unmodifiableMap(map);
        this.totalWeight = weightSum;
    }

    public List<Validator> getValidators() {
        return validators;
    }

    /**
     * 按 20 字节节点地址查找验证者。
     *
     * @param nodeId 节点地址
     * @return 验证者，不存在返回 null
     */
    public Validator getByNodeId(byte[] nodeId) {
        if (nodeId == null) {
            return null;
        }
        return byId.get(CryptoUtils.toHex(nodeId));
    }

    /**
     * 是否为验证者。
     *
     * @param nodeId 节点地址
     * @return 是否在集合内
     */
    public boolean isValidator(byte[] nodeId) {
        return getByNodeId(nodeId) != null;
    }

    public int size() {
        return validators.size();
    }

    public long getTotalWeight() {
        return totalWeight;
    }

    /**
     * 法定人数：floor(2n/3) + 1。
     *
     * @return quorum 票数
     */
    public int quorum() {
        return (2 * validators.size()) / 3 + 1;
    }
}
