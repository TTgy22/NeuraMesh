package com.neuramesh.jmh;

import com.neuramesh.core.CryptoUtils;
import com.neuramesh.core.Transaction;
import com.neuramesh.core.TxType;
import com.neuramesh.network.codec.TransactionCodec;
import java.security.KeyPair;
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
 * 网络广播基准：模拟 8 节点 Gossip 扇出的交易消息编解码开销（确定性，无真实 socket）。
 *
 * <p>每个 op：发送端编码一次交易，8 个对端各解码一次（重建并重算 txId），测量消息处理吞吐与单跳延迟。
 */
@State(Scope.Thread)
public class NetworkBenchmark {

    private static final int PEERS = 8;

    private Transaction tx;
    private byte[] encoded;

    @Setup
    public void setup() {
        KeyPair kp = CryptoUtils.generateKeyPair();
        byte[] from = CryptoUtils.toAddress(kp.getPublic());
        byte[] to = CryptoUtils.toAddress(CryptoUtils.generateKeyPair().getPublic());
        byte[] payload = new byte[128];
        Transaction t = Transaction.create(TxType.TOKEN_TRANSFER, from, to, 0, payload,
                1_700_000_000_000L);
        tx = t.withSignature(CryptoUtils.sign(t.signingBytes(), kp.getPrivate()));
        encoded = TransactionCodec.encode(tx);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void gossipFanout(Blackhole bh) {
        byte[] wire = TransactionCodec.encode(tx);
        for (int i = 0; i < PEERS; i++) {
            bh.consume(TransactionCodec.decode(wire));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void singleHopDecode(Blackhole bh) {
        bh.consume(TransactionCodec.decode(encoded));
    }
}
