package com.neuramesh.jmh;

import com.neuramesh.consensus.bft.Validator;
import com.neuramesh.consensus.bft.ValidatorSet;
import com.neuramesh.core.CryptoUtils;
import com.neuramesh.core.Transaction;
import com.neuramesh.core.TxType;
import com.neuramesh.vm.StateMachine;
import com.neuramesh.vm.payload.TokenTransferPayload;
import com.neuramesh.vm.state.GlobalState;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/**
 * 状态机吞吐基准：连续执行 TOKEN_TRANSFER，测量每秒可处理交易数（TPS）。
 *
 * <p>Throughput 模式，单位 ops/s 即 tx/s。发起方预注资大额，向固定 100 个收款账户轮转转账，
 * 账户集有界（Merkle commit 成本稳定），nonce 单调递增。
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
public class StateMachineBenchmark {

    private static final int RECIPIENTS = 100;

    private StateMachine stateMachine;
    private GlobalState state;
    private KeyPair senderKey;
    private byte[] sender;
    private byte[][] recipients;
    private long nonce;

    @Setup(Level.Iteration)
    public void setup() {
        List<Validator> vs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            KeyPair kp = CryptoUtils.generateKeyPair();
            vs.add(new Validator(CryptoUtils.toAddress(kp.getPublic()), kp.getPublic(), 1, 0));
        }
        stateMachine = StateMachine.standard(new ValidatorSet(vs));
        state = new GlobalState();

        senderKey = CryptoUtils.generateKeyPair();
        sender = CryptoUtils.toAddress(senderKey.getPublic());
        state.credit(sender, Long.MAX_VALUE / 2);

        recipients = new byte[RECIPIENTS][];
        for (int i = 0; i < RECIPIENTS; i++) {
            recipients[i] = CryptoUtils.toAddress(CryptoUtils.generateKeyPair().getPublic());
        }
        nonce = 0;
    }

    @Benchmark
    public void tokenTransfer(Blackhole bh) {
        byte[] to = recipients[(int) (nonce % RECIPIENTS)];
        TokenTransferPayload p = new TokenTransferPayload(1L);
        Transaction tx = Transaction.create(TxType.TOKEN_TRANSFER, sender, to, nonce, p.encode(),
                1_700_000_000_000L + nonce);
        tx = tx.withSignature(CryptoUtils.sign(tx.signingBytes(), senderKey.getPrivate()));
        byte[] root = stateMachine.apply(tx, state);
        nonce++;
        bh.consume(root);
    }
}
