package com.cattlefarm.admin.report;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.cattlefarm.common.api.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    @SaCheckRole(value = {"ADMIN", "FARM_MANAGER"}, mode = SaMode.OR)
    public ApiResponse<ReportDtos.Overview> overview(
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ApiResponse.success(service.overview(start, end));
    }

    @GetMapping("/inventory")
    @SaCheckRole(value = {"ADMIN", "FARM_MANAGER", "VET", "BREEDER"}, mode = SaMode.OR)
    public ApiResponse<ReportDtos.Inventory> inventory(
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ApiResponse.success(service.inventory(start, end));
    }

    @GetMapping("/breeding")
    @SaCheckRole(value = {"ADMIN", "FARM_MANAGER", "VET", "BREEDER"}, mode = SaMode.OR)
    public ApiResponse<ReportDtos.Section> breeding(
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ApiResponse.success(service.breeding(start, end));
    }

    @GetMapping("/health")
    @SaCheckRole(value = {"ADMIN", "FARM_MANAGER", "VET", "BREEDER"}, mode = SaMode.OR)
    public ApiResponse<ReportDtos.Section> health(
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ApiResponse.success(service.health(start, end));
    }

    @GetMapping("/tasks")
    @SaCheckRole(value = {"ADMIN", "FARM_MANAGER", "VET", "BREEDER"}, mode = SaMode.OR)
    public ApiResponse<ReportDtos.Section> tasks(
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ApiResponse.success(service.tasks(start, end));
    }
}
