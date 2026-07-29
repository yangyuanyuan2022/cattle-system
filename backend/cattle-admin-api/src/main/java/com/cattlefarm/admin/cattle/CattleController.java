package com.cattlefarm.admin.cattle;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.cattlefarm.admin.cattle.service.CattleService;
import com.cattlefarm.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/cattle")
public class CattleController {
    private final CattleService cattleService;

    public CattleController(CattleService cattleService) {
        this.cattleService = cattleService;
    }

    @GetMapping
    public ApiResponse<CattlePageResponse> page(
            @RequestParam(name = "page", defaultValue = "1") @Min(1) long page,
            @RequestParam(name = "pageSize", defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "presenceStatus", required = false) String presenceStatus,
            @RequestParam(name = "lifecycleStage", required = false) String lifecycleStage,
            @RequestParam(name = "sex", required = false) String sex,
            @RequestParam(name = "breedId", required = false) String breedId,
            @RequestParam(name = "sourceType", required = false) String sourceType,
            @RequestParam(name = "healthStatus", required = false) String healthStatus,
            @RequestParam(name = "barnId", required = false) String barnId) {
        return ApiResponse.success(cattleService.page(page, pageSize, keyword, presenceStatus, lifecycleStage, sex, breedId, sourceType, healthStatus, barnId));
    }

    @PostMapping
    @SaCheckRole(value = {"ADMIN", "FARM_MANAGER"}, mode = SaMode.OR)
    public ApiResponse<CattleResponse> create(
            @RequestHeader("X-Idempotency-Key") @NotBlank String idempotencyKey,
            @Valid @RequestBody CreateCattleRequest request) {
        return ApiResponse.success(cattleService.create(request, idempotencyKey));
    }

    @GetMapping("/{cattleId}")
    public ApiResponse<CattleResponse> detail(@PathVariable("cattleId") long cattleId) {
        return ApiResponse.success(cattleService.detail(cattleId));
    }

    @PutMapping("/{cattleId}")
    @SaCheckRole(value = {"ADMIN", "FARM_MANAGER"}, mode = SaMode.OR)
    public ApiResponse<CattleResponse> update(
            @PathVariable("cattleId") long cattleId,
            @RequestHeader("X-Idempotency-Key") @NotBlank String idempotencyKey,
            @Valid @RequestBody UpdateCattleRequest request) {
        return ApiResponse.success(cattleService.update(cattleId, request, idempotencyKey));
    }

    @PostMapping("/{cattleId}/archive")
    @SaCheckRole(value = {"ADMIN", "FARM_MANAGER"}, mode = SaMode.OR)
    public ApiResponse<CattleResponse> archive(
            @PathVariable("cattleId") long cattleId,
            @RequestHeader("X-Idempotency-Key") @NotBlank String idempotencyKey,
            @Valid @RequestBody ArchiveCattleRequest request) {
        return ApiResponse.success(cattleService.archive(cattleId, request, idempotencyKey));
    }

    @PostMapping("/{cattleId}/restore")
    @SaCheckRole(value = {"ADMIN", "FARM_MANAGER"}, mode = SaMode.OR)
    public ApiResponse<CattleResponse> restore(
            @PathVariable("cattleId") long cattleId,
            @RequestHeader("X-Idempotency-Key") @NotBlank String idempotencyKey,
            @Valid @RequestBody RestoreCattleRequest request) {
        return ApiResponse.success(cattleService.restore(cattleId, request, idempotencyKey));
    }

    @GetMapping("/{cattleId}/timeline")
    public ApiResponse<List<CattleTimelineEventResponse>> timeline(@PathVariable("cattleId") long cattleId) {
        return ApiResponse.success(cattleService.timeline(cattleId));
    }

    @GetMapping("/{cattleId}/pedigree")
    public ApiResponse<CattlePedigreeResponse> pedigree(@PathVariable("cattleId") long cattleId) {
        return ApiResponse.success(cattleService.pedigree(cattleId));
    }
}
