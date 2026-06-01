package com.neuramesh.consensus.bft;

import com.neuramesh.consensus.exception.ConsensusException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 投票收集器。
 *
 * <p>结构：{@code blockHashHex -> (VoteType -> (validatorIdHex -> Vote))}，全程使用
 * {@link ConcurrentHashMap}，无显式锁。
 *
 * <p>去重：同一验证者对同一区块的同一阶段只算一票（按 validatorId 覆盖）。
 *
 * <p>法定人数由构造时传入的 {@code quorum} 决定（= {@link ValidatorSet#quorum()}）。
 */
public final class VoteCollector {

    private final int quorum;
    private final Map<String, Map<VoteType, Map<String, Vote>>> votes = new ConcurrentHashMap<>();

    public VoteCollector(int quorum) {
        if (quorum <= 0) {
            throw new ConsensusException("quorum 必须为正: " + quorum);
        }
        this.quorum = quorum;
    }

    /**
     * 加入一票（按 blockHash + 阶段 + validatorId 去重）。
     *
     * @param vote 投票
     * @return 是否为新票（false 表示重复，已存在）
     */
    public boolean addVote(Vote vote) {
        if (vote == null) {
            throw new ConsensusException("vote 不可为 null");
        }
        Map<VoteType, Map<String, Vote>> byPhase =
                votes.computeIfAbsent(vote.getBlockHashHex(), k -> new ConcurrentHashMap<>());
        Map<String, Vote> byValidator =
                byPhase.computeIfAbsent(vote.getType(), k -> new ConcurrentHashMap<>());
        return byValidator.putIfAbsent(vote.getValidatorIdHex(), vote) == null;
    }

    /**
     * 某区块某阶段当前票数。
     *
     * @param blockHashHex 区块哈希 hex
     * @param type         阶段
     * @return 票数
     */
    public int count(String blockHashHex, VoteType type) {
        Map<VoteType, Map<String, Vote>> byPhase = votes.get(blockHashHex);
        if (byPhase == null) {
            return 0;
        }
        Map<String, Vote> byValidator = byPhase.get(type);
        return byValidator == null ? 0 : byValidator.size();
    }

    /**
     * 是否已达法定人数。
     *
     * @param blockHashHex 区块哈希 hex
     * @param type         阶段
     * @return 是否 &ge; quorum
     */
    public boolean hasQuorum(String blockHashHex, VoteType type) {
        return count(blockHashHex, type) >= quorum;
    }

    public int getQuorum() {
        return quorum;
    }
}
