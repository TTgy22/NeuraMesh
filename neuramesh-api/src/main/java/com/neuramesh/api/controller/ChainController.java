package com.neuramesh.api.controller;

import com.neuramesh.api.common.ApiResponse;
import com.neuramesh.api.dto.BlockInfoDTO;
import com.neuramesh.api.dto.ChainStatsDTO;
import com.neuramesh.api.dto.TxInfoDTO;
import com.neuramesh.api.service.ChainService;
import com.neuramesh.core.CryptoUtils;
import com.neuramesh.vm.state.NodeState;
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

    public ChainController(ChainService chainService) {
        this.chainService = chainService;
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
