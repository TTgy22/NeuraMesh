package com.neuramesh.consensus.bft;

import com.neuramesh.consensus.exception.ConsensusException;
import java.util.List;

/**
 * 提案人选择器：加权轮询（按 weight 比例），伪随机但确定性。
 *
 * <p>算法：{@code pos = ((height + seed) mod totalWeight)}，按验证者顺序累加权重，
 * 落入区间者当选。相同 {@code (height, seed, validators)} 必得相同结果，保证全网一致。
 *
 * <p>视图变更时以 {@code height + view} 作为 round 传入，即可确定性地切换到下一提案人。
 */
public final class ProposerSelector {

    private final long seed;

    public ProposerSelector(long seed) {
        this.seed = seed;
    }

    /**
     * 选择某 round 的提案人。
     *
     * @param round      轮次（通常 = height + view）
     * @param validators 验证者列表（顺序需全网一致）
     * @return 当选验证者
     */
    public Validator selectProposer(long round, List<Validator> validators) {
        if (validators == null || validators.isEmpty()) {
            throw new ConsensusException("验证者列表不可为空");
        }
        long totalWeight = 0;
        for (Validator v : validators) {
            totalWeight += v.getWeight();
        }
        // Math.floorMod 保证非负（round + seed 可能为负或溢出取模仍稳定）
        long pos = Math.floorMod(round + seed, totalWeight);
        long acc = 0;
        for (Validator v : validators) {
            acc += v.getWeight();
            if (pos < acc) {
                return v;
            }
        }
        // 理论不可达（pos < totalWeight）
        return validators.get(validators.size() - 1);
    }

    public long getSeed() {
        return seed;
    }
}
