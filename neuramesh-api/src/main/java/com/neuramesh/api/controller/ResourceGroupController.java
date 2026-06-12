package com.neuramesh.api.controller;

import com.neuramesh.api.common.ApiResponse;
import com.neuramesh.api.dto.NodeStatusDTO;
import com.neuramesh.api.dto.PurchaseReceiptDTO;
import com.neuramesh.api.dto.PurchaseRequest;
import com.neuramesh.api.dto.ResourceGroupDTO;
import com.neuramesh.api.dto.TaskStatusDTO;
import com.neuramesh.api.security.UserPrincipal;
import com.neuramesh.api.service.ResourceGroupService;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资源组 REST API：组管理（/groups）+ 市场购买（/market）+ 我的资源组（/vendor/groups）。
 */
@RestController
public class ResourceGroupController {

    private final ResourceGroupService groupService;

    public ResourceGroupController(ResourceGroupService groupService) {
        this.groupService = groupService;
    }

    // ---- 组管理（公开，既有 dashboard 使用）----

    @GetMapping("/groups")
    public ApiResponse<List<ResourceGroupDTO>> list() {
        return ApiResponse.ok(groupService.list());
    }

    @GetMapping("/groups/{id}")
    public ApiResponse<ResourceGroupDTO> detail(@PathVariable("id") String id) {
        ResourceGroupDTO dto = groupService.detail(id);
        if (dto == null) {
            return ApiResponse.error(404, "资源组不存在: " + id);
        }
        return ApiResponse.ok(dto);
    }

    @GetMapping("/groups/{id}/nodes")
    public ApiResponse<List<NodeStatusDTO>> nodes(@PathVariable("id") String id) {
        List<NodeStatusDTO> nodes = groupService.nodesOf(id);
        if (nodes == null) {
            return ApiResponse.error(404, "资源组不存在: " + id);
        }
        return ApiResponse.ok(nodes);
    }

    @PostMapping("/groups/{id}/join")
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

    @PostMapping("/groups/{id}/allocate")
    public ApiResponse<TaskStatusDTO> allocate(@PathVariable("id") String id,
                                               @RequestBody(required = false) Map<String, Object> body) {
        String vendorId = body == null ? null : asString(body.get("vendorId"));
        String taskType = body == null ? null : asString(body.get("taskType"));
        long budget = 0;
        if (body != null && body.get("budget") instanceof Number n) {
            budget = n.longValue();
        }
        // 默认 6s 模拟计算（RUNNING → 真实上链 SETTLED）；传 0 可即时结算
        long simulateMs = 6000;
        if (body != null && body.get("simulateMs") instanceof Number sm) {
            simulateMs = sm.longValue();
        }
        return ApiResponse.ok(groupService.allocateTask(id, vendorId, taskType, budget, simulateMs));
    }

    /** 全部组任务列表（新在前）：历史任务的权威数据源，前端切页/刷新不丢。 */
    @GetMapping("/groups/tasks")
    public ApiResponse<List<TaskStatusDTO>> groupTasks() {
        return ApiResponse.ok(groupService.allGroupTasks());
    }

    /** 组任务状态查询（前端轮询 RUNNING → SETTLED/FAILED）。 */
    @GetMapping("/groups/tasks/{taskId}")
    public ApiResponse<TaskStatusDTO> groupTask(@PathVariable("taskId") String taskId) {
        TaskStatusDTO dto = groupService.groupTask(taskId);
        if (dto == null) {
            return ApiResponse.error(404, "任务不存在: " + taskId);
        }
        return ApiResponse.ok(dto);
    }

    // ---- 市场（浏览公开，购买/续费需认证）----

    @GetMapping("/market/groups")
    public ApiResponse<List<ResourceGroupDTO>> market() {
        return ApiResponse.ok(groupService.list());
    }

    @GetMapping("/market/groups/{id}")
    public ApiResponse<ResourceGroupDTO> marketDetail(@PathVariable("id") String id) {
        ResourceGroupDTO dto = groupService.detail(id);
        if (dto == null) {
            return ApiResponse.error(404, "资源组不存在: " + id);
        }
        return ApiResponse.ok(dto);
    }

    @PostMapping("/market/groups/{id}/buy")
    public ApiResponse<PurchaseReceiptDTO> buy(@PathVariable("id") String id,
                                               @RequestBody PurchaseRequest req,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ApiResponse.error(401, "未认证");
        }
        try {
            return ApiResponse.ok(groupService.buy(id, principal, req == null ? 0 : req.hours()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/market/groups/{id}/renew")
    public ApiResponse<PurchaseReceiptDTO> renew(@PathVariable("id") String id,
                                                 @RequestBody PurchaseRequest req,
                                                 @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ApiResponse.error(401, "未认证");
        }
        try {
            return ApiResponse.ok(groupService.renew(id, principal, req == null ? 0 : req.hours()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    // ---- 我的资源组（需认证）----

    @GetMapping("/vendor/groups")
    public ApiResponse<List<Map<String, Object>>> myGroups(
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ApiResponse.error(401, "未认证");
        }
        return ApiResponse.ok(groupService.myGroups(principal.userId()));
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }
}
