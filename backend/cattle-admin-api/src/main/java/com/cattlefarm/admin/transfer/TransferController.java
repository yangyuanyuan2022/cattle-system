package com.cattlefarm.admin.transfer;

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
@RequestMapping("/api/v1/transfers")
public class TransferController {
    private final TransferService service;
    private final EnhancedTransferService enhancedService;

    public TransferController(TransferService service, EnhancedTransferService enhancedService) {
        this.service = service;
        this.enhancedService = enhancedService;
    }

    @GetMapping
    public ApiResponse<List<TransferItem>> list() {
        return ApiResponse.success(enhancedService.list());
    }

    @PostMapping
    @SaCheckRole(value = {"ADMIN", "FARM_MANAGER", "WORKER"}, mode = SaMode.OR)
    public ApiResponse<TransferResponse> create(
            @RequestHeader("X-Idempotency-Key") @NotBlank String key,
            @Valid @RequestBody CreateTransferRequest request) {
        return ApiResponse.success(service.transfer(request, key));
    }

    @PostMapping("/batch")
    @SaCheckRole(value = {"ADMIN", "FARM_MANAGER"}, mode = SaMode.OR)
    public ApiResponse<List<TransferResponse>> batch(
            @RequestHeader("X-Idempotency-Key") @NotBlank String key,
            @Valid @RequestBody BatchTransferRequest request) {
        return ApiResponse.success(enhancedService.batch(request, key));
    }

    @PostMapping("/{transferId}/void")
    @SaCheckRole(value = {"ADMIN", "FARM_MANAGER"}, mode = SaMode.OR)
    public ApiResponse<TransferItem> voidTransfer(
            @PathVariable long transferId,
            @RequestHeader("X-Idempotency-Key") @NotBlank String key,
            @Valid @RequestBody VoidTransferRequest request) {
        return ApiResponse.success(enhancedService.voidTransfer(transferId, request, key));
    }
}
