package com.cattlefarm.admin.exit;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.cattlefarm.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/exits")
public class ExitController {
    private final ExitService service;

    public ExitController(ExitService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ExitItem>> list() {
        return ApiResponse.success(service.list());
    }

    @PostMapping("/{exitId}/void")
    @SaCheckRole(value = {"ADMIN", "FARM_MANAGER"}, mode = SaMode.OR)
    public ApiResponse<ExitItem> voidExit(
            @PathVariable long exitId,
            @RequestHeader("X-Idempotency-Key") @NotBlank String key,
            @Valid @RequestBody VoidExitRequest request) {
        return ApiResponse.success(service.voidExit(exitId, request, key));
    }
}
