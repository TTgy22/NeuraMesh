package com.neuramesh.api.controller;

import com.neuramesh.api.common.ApiResponse;
import com.neuramesh.api.dto.NodeStatusDTO;
import com.neuramesh.api.dto.ResourceGroupDTO;
import com.neuramesh.api.dto.TaskStatusDTO;
import com.neuramesh.api.service.ResourceGroupService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资源组 REST API：列出/详情/加入/组内节点/按组分配任务。
 */
@RestController
@RequestMapping("/groups")
public class ResourceGroupController {

    private final ResourceGroupService groupService;

    public ResourceGroupController(ResourceGroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public ApiResponse<List<ResourceGroupDTO>> list() {
        return ApiResponse.ok(groupService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<ResourceGroupDTO> detail(@PathVariable("id") String id) {
        ResourceGroupDTO dto = groupService.detail(id);
        if (dto == null) {
            return ApiResponse.error(404, "资源组不存在: " + id);
        }
        return ApiResponse.ok(dto);
    }

    @GetMapping("/{id}/nodes")
    public ApiResponse<List<NodeStatusDTO>> nodes(@PathVariable("id") String id) {
        List<NodeStatusDTO> nodes = groupService.nodesOf(id);
        if (nodes == null) {
            return ApiResponse.error(404, "资源组不存在: " + id);
        }
        return ApiResponse.ok(nodes);
    }

    @PostMapping("/{id}/join")
    public ApiResponse<ResourceGroupDTO> join(@PathVariable("id") String id,
                                              @RequestBody Map<String, String> body) {
        String nodeId = body == null ? null : body.get("nodeId");
        if (nodeId == null || nodeId.isBlank()) {
            return ApiResponse.error(400, "缺少 nodeId");
        }
        try {
            return ApiResponse.ok(groupService.join(id, nodeId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/{id}/allocate")
    public ApiResponse<TaskStatusDTO> allocate(@PathVariable("id") String id,
                                               @RequestBody(required = false) Map<String, Object> body) {
        String vendorId = body == null ? null : asString(body.get("vendorId"));
        String taskType = body == null ? null : asString(body.get("taskType"));
        long budget = 0;
        if (body != null && body.get("budget") instanceof Number n) {
            budget = n.longValue();
        }
        return ApiResponse.ok(groupService.allocateTask(id, vendorId, taskType, budget));
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }
}
