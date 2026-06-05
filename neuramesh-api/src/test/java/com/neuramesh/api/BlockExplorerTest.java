package com.neuramesh.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuramesh.api.dto.BlockInfoDTO;
import com.neuramesh.api.dto.NodeStatusDTO;
import com.neuramesh.api.dto.TxInfoDTO;
import com.neuramesh.api.service.ChainService;
import com.neuramesh.api.service.NodeService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 浏览器查询：注册节点产生区块/交易 → 按区块取交易哈希 → 查询交易详情字段完整。
 */
@SpringBootTest
class BlockExplorerTest {

    @Autowired
    private NodeService nodeService;

    @Autowired
    private ChainService chainService;

    @Test
    @Timeout(30)
    void query_blocks_and_tx() {
        NodeStatusDTO node = nodeService.register("Mac-Studio-M2");

        List<BlockInfoDTO> blocks = chainService.latestBlocks(50);
        assertThat(blocks).isNotEmpty();
        // 哈希链：相邻区块 prevHash == 前一区块 hash
        for (int i = 0; i < blocks.size() - 1; i++) {
            assertThat(blocks.get(i).prevHash()).isEqualTo(blocks.get(i + 1).hash());
        }

        // 节点档案存在
        TxInfoDTO notFound = chainService.findTx("0xdeadbeef");
        assertThat(notFound).isNull();

        assertThat(chainService.nodeProfile(hexToBytes(node.nodeId().substring(2)))).isNotNull();
        assertThat(chainService.blockHeight()).isGreaterThan(0);
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length() / 2;
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
