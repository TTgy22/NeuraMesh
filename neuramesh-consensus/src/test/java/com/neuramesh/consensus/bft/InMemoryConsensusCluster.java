package com.neuramesh.consensus.bft;

import com.neuramesh.consensus.TxPool;
import com.neuramesh.consensus.block.BlockProducer;
import com.neuramesh.consensus.block.BlockStore;
import com.neuramesh.consensus.block.InMemoryBlockStore;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 确定性内存共识集群（测试传输）。
 *
 * <p>用单线程 FIFO 队列模拟网络广播：节点 i 广播某消息时，向所有其他节点入队一个投递任务；
 * {@link #pump()} 依次执行队列直到清空。整个过程单线程、确定性，无端口、无真实网络、无时序抖动，
 * 用以稳定验证 PBFT 共识逻辑（详见 Pause 2 复盘的传输决策说明）。
 */
final class InMemoryConsensusCluster {

    final List<BFTConsensus> nodes = new ArrayList<>();
    final List<BlockStore> stores = new ArrayList<>();
    private final Deque<Runnable> queue = new ArrayDeque<>();

    InMemoryConsensusCluster(TestValidators tv, long seed) {
        ProposerSelector selector = new ProposerSelector(seed);
        for (int i = 0; i < tv.keyPairs.size(); i++) {
            BlockStore store = new InMemoryBlockStore();
            BlockProducer producer = new BlockProducer(new TxPool());
            BlockFinality finality = new BlockFinality();
            BusBroadcaster broadcaster = new BusBroadcaster(i);
            BFTConsensus node = new BFTConsensus(tv.keyOf(i), tv.set, selector,
                    producer, store, finality, broadcaster);
            nodes.add(node);
            stores.add(store);
        }
    }

    /**
     * 在所有节点上开始指定高度的共识并跑到稳定（队列清空）。
     *
     * @param height 高度
     */
    void runRound(long height) {
        for (BFTConsensus node : nodes) {
            node.startConsensus(height);
        }
        pump();
    }

    void pump() {
        int guard = 0;
        while (!queue.isEmpty()) {
            queue.poll().run();
            if (++guard > 1_000_000) {
                throw new IllegalStateException("消息泵超过上限，疑似死循环");
            }
        }
    }

    BFTConsensus node(int i) {
        return nodes.get(i);
    }

    /** 每个节点对应的广播器：把消息投递给其他所有节点。 */
    private final class BusBroadcaster implements ConsensusBroadcaster {
        private final int self;

        BusBroadcaster(int self) {
            this.self = self;
        }

        @Override
        public void broadcastPrePrepare(PrePrepare pp) {
            for (int j = 0; j < nodes.size(); j++) {
                if (j != self) {
                    BFTConsensus target = nodes.get(j);
                    queue.add(() -> target.onPrePrepare(pp));
                }
            }
        }

        @Override
        public void broadcastPrepare(Vote vote) {
            for (int j = 0; j < nodes.size(); j++) {
                if (j != self) {
                    BFTConsensus target = nodes.get(j);
                    queue.add(() -> target.onPrepare(vote));
                }
            }
        }

        @Override
        public void broadcastCommit(Vote vote) {
            for (int j = 0; j < nodes.size(); j++) {
                if (j != self) {
                    BFTConsensus target = nodes.get(j);
                    queue.add(() -> target.onCommit(vote));
                }
            }
        }
    }
}
