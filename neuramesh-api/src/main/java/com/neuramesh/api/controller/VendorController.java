package com.neuramesh.api.controller;

import com.neuramesh.api.common.ApiResponse;
import com.neuramesh.api.dto.TaskStatusDTO;
import com.neuramesh.api.dto.TaskSubmitDTO;
import com.neuramesh.api.service.VendorService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 厂商 REST API。
 */
@RestController
public class VendorController {

    private final VendorService vendorService;

    public VendorController(VendorService vendorService) {
        this.vendorService = vendorService;
    }

    @PostMapping("/task/submit")
    public ApiResponse<TaskStatusDTO> submit(@RequestBody TaskSubmitDTO dto) {
        return ApiResponse.ok(vendorService.submit(dto));
    }

    @GetMapping("/task/{id}/status")
    public ApiResponse<TaskStatusDTO> status(@PathVariable("id") String id) {
        TaskStatusDTO dto = vendorService.status(id);
        if (dto == null) {
            return ApiResponse.error(404, "任务不存在: " + id);
        }
        return ApiResponse.ok(dto);
    }

    @GetMapping("/task/{id}/result")
    public ApiResponse<TaskStatusDTO> result(@PathVariable("id") String id) {
        TaskStatusDTO dto = vendorService.result(id);
        if (dto == null) {
            return ApiResponse.error(404, "任务不存在: " + id);
        }
        return ApiResponse.ok(dto);
    }

    @GetMapping("/vendor/{id}/balance")
    public ApiResponse<Map<String, Long>> balance(@PathVariable("id") String id) {
        return ApiResponse.ok(Map.of("balance", vendorService.balance(id)));
    }
}
