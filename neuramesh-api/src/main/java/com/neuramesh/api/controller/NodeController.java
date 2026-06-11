package com.neuramesh.api.controller;

import com.neuramesh.api.common.ApiResponse;
import com.neuramesh.api.dto.EarningsPointDTO;
import com.neuramesh.api.dto.NodeRegisterRequest;
import com.neuramesh.api.dto.NodeStatusDTO;
import com.neuramesh.api.service.NodeService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 节点 REST API。
 */
@RestController
@RequestMapping("/node")
public class NodeController {

    private final NodeService nodeService;

    public NodeController(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @PostMapping("/register")
    public ApiResponse<NodeStatusDTO> register(@RequestBody(required = false) NodeRegisterRequest req) {
        String model = req == null ? null : req.deviceModel();
        String groupId = req == null ? null : req.resourceGroupId();
        return ApiResponse.ok(nodeService.register(model, groupId));
    }

    @GetMapping("/list")
    public ApiResponse<List<NodeStatusDTO>> list() {
        return ApiResponse.ok(nodeService.allNodeStatuses());
    }

    @GetMapping("/{id}/status")
    public ApiResponse<NodeStatusDTO> status(@PathVariable("id") String id) {
        NodeStatusDTO dto = nodeService.status(id);
        if (dto == null) {
            return ApiResponse.error(404, "节点不存在: " + id);
        }
        return ApiResponse.ok(dto);
    }

    @PostMapping("/start")
    public ApiResponse<NodeStatusDTO> start(@RequestParam("id") String id) {
        NodeStatusDTO dto = nodeService.start(id);
        if (dto == null) {
            return ApiResponse.error(404, "节点不存在: " + id);
        }
        return ApiResponse.ok(dto);
    }

    @PostMapping("/stop")
    public ApiResponse<NodeStatusDTO> stop(@RequestParam("id") String id) {
        NodeStatusDTO dto = nodeService.stop(id);
        if (dto == null) {
            return ApiResponse.error(404, "节点不存在: " + id);
        }
        return ApiResponse.ok(dto);
    }

    @GetMapping("/{id}/earnings")
    public ApiResponse<List<EarningsPointDTO>> earnings(@PathVariable("id") String id,
                                                        @RequestParam(value = "days", defaultValue = "7") int days) {
        return ApiResponse.ok(nodeService.earnings(id, days));
    }
}
