package com.neuramesh.jmh;

import com.neuramesh.consensus.bft.ProposerSelector;
import com.neuramesh.consensus.bft.Validator;
import com.neuramesh.consensus.bft.ValidatorSet;
import com.neuramesh.consensus.bft.Vote;
import com.neuramesh.consensus.bft.VoteCollector;
import com.neuramesh.consensus.bft.VoteType;
import com.neuramesh.core.CryptoUtils;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/**
 * BFT 共识基准：模拟 8 验证者的一轮 PBFT 投票（PREPARE + COMMIT），含真实 ECDSA 签名与验签，
 * 直到达成法定人数。
 *
 * <ul>
 *   <li>{@link #roundThroughput} Throughput：每秒可完成的共识轮数（rounds/s）；</li>
 *   <li>{@link #roundLatency} AverageTime：单轮最终化平均耗时（ms/round，对应区块最终化时间）。</li>
 * </ul>
 */
@State(Scope.Thread)
public class ConsensusBenchmark {

    private static final int N = 8;

    private List<KeyPair> keys;
    private ValidatorSet validators;
    private ProposerSelector selector;
    private byte[] blockHash;
    private long round;

    @Setup
    public void setup() {
        keys = new ArrayList<>();
        List<Validator> vs = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            KeyPair kp = CryptoUtils.generateKeyPair();
            keys.add(kp);
            vs.add(new Validator(CryptoUtils.toAddress(kp.getPublic()), kp.getPublic(), 1, 0));
        }
        validators = new ValidatorSet(vs);
        selector = new ProposerSelector(42L);
        blockHash = CryptoUtils.sha256("neuramesh-genesis-block".getBytes());
        round = 0;
    }

    /** 一轮完整投票：选提案人 + 两阶段签名/验签/收集，直到双阶段达成 quorum。 */
    private boolean oneRound(long r) {
        selector.selectProposer(r, validators.getValidators());
        VoteCollector collector = new VoteCollector(validators.quorum());
        String hashHex = CryptoUtils.toHex(blockHash);

        boolean finalized = false;
        for (VoteType phase : new VoteType[] {VoteType.PREPARE, VoteType.COMMIT}) {
            for (int i = 0; i < N; i++) {
                KeyPair kp = keys.get(i);
                byte[] vid = CryptoUtils.toAddress(kp.getPublic());
                byte[] sig = CryptoUtils.sign(Vote.signingBytes(phase, blockHash), kp.getPrivate());
                Vote vote = new Vote(phase, blockHash, vid, sig);
                if (vote.verify(validators)) {
                    collector.addVote(vote);
                }
                if (collector.hasQuorum(hashHex, phase)) {
                    break;
                }
            }
            finalized = collector.hasQuorum(hashHex, phase);
        }
        return finalized;
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void roundThroughput(Blackhole bh) {
        bh.consume(oneRound(round++));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void roundLatency(Blackhole bh) {
        bh.consume(oneRound(round++));
    }
}
