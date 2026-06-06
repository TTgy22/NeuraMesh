package com.neuramesh.api.controller;

import com.neuramesh.api.common.ApiResponse;
import com.neuramesh.api.dto.BlockInfoDTO;
import com.neuramesh.api.dto.ChainStatsDTO;
import com.neuramesh.api.dto.NodeStatusDTO;
import com.neuramesh.api.dto.TxInfoDTO;
import com.neuramesh.api.service.ChainService;
import com.neuramesh.api.service.NodeService;
import com.neuramesh.core.CryptoUtils;
import com.neuramesh.vm.state.NodeState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 区块链浏览器 REST API。
 */
@RestController
@RequestMapping("/chain")
public class ChainController {

    private final ChainService chainService;
    private final NodeService nodeService;

    public ChainController(ChainService chainService, NodeService nodeService) {
        this.chainService = chainService;
        this.nodeService = nodeService;
    }

    /**
     * 节点列表（用于可视化图表）。
     *
     * <ul>
     *   <li>{@code groupBy=level}：返回 [{level, count}...] 等级分布（环形图）；</li>
     *   <li>{@code sortBy=weight}：按总权重降序返回节点列表（柱状图）；</li>
     *   <li>无参：原始节点列表。</li>
     * </ul>
     */
    @GetMapping("/nodes")
    public ApiResponse<Object> nodes(
            @RequestParam(value = "groupBy", required = false) String groupBy,
            @RequestParam(value = "sortBy", required = false) String sortBy) {
        List<NodeStatusDTO> nodes = nodeService.allNodeStatuses();
        if ("level".equals(groupBy)) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (String lv : new String[] {"钻石", "铂金", "黄金", "白银", "青铜"}) {
                counts.put(lv, 0);
            }
            for (NodeStatusDTO n : nodes) {
                counts.merge(n.level(), 1, Integer::sum);
            }
            List<Map<String, Object>> dist = new ArrayList<>();
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                dist.add(Map.of("level", e.getKey(), "count", e.getValue()));
            }
            return ApiResponse.ok(dist);
        }
        if ("weight".equals(sortBy)) {
            nodes = new ArrayList<>(nodes);
            nodes.sort(Comparator.comparingDouble(NodeStatusDTO::totalWeight).reversed());
        }
        return ApiResponse.ok(nodes);
    }

    @GetMapping("/stats")
    public ApiResponse<ChainStatsDTO> stats() {
        return ApiResponse.ok(chainService.stats());
    }

    @GetMapping("/blocks")
    public ApiResponse<List<BlockInfoDTO>> blocks(
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return ApiResponse.ok(chainService.latestBlocks(limit));
    }

    @GetMapping("/tx/{hash}")
    public ApiResponse<TxInfoDTO> tx(@PathVariable("hash") String hash) {
        TxInfoDTO dto = chainService.findTx(hash);
        if (dto == null) {
            return ApiResponse.error(404, "交易不存在: " + hash);
        }
        return ApiResponse.ok(dto);
    }

    @GetMapping("/node/{id}")
    public ApiResponse<Map<String, Object>> nodeProfile(@PathVariable("id") String id) {
        String hex = id.startsWith("0x") ? id.substring(2) : id;
        NodeState ns = chainService.nodeProfile(hexToBytes(hex));
        if (ns == null) {
            return ApiResponse.error(404, "节点不存在: " + id);
        }
        return ApiResponse.ok(Map.of(
                "nodeId", "0x" + ns.getNodeIdHex(),
                "totalWeight", ns.getTotalWeight(),
                "totalEarned", ns.getTotalEarned(),
                "fingerprint", CryptoUtils.toHex(ns.getFingerprint())));
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
