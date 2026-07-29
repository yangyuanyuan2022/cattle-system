package com.cattlefarm.admin.vaccination;
import cn.dev33.satoken.annotation.*;import com.cattlefarm.common.api.ApiResponse;import com.cattlefarm.admin.correction.*;import jakarta.validation.Valid;import jakarta.validation.constraints.NotBlank;import org.springframework.web.bind.annotation.*;import java.util.List;
@RestController @RequestMapping("/api/v1/vaccinations")
public class VaccinationController {private final VaccinationService service;private final BusinessCorrectionService corrections;public VaccinationController(VaccinationService service,BusinessCorrectionService corrections){this.service=service;this.corrections=corrections;}
 @GetMapping("/plans") public ApiResponse<List<VaccinationDtos.PlanItem>> plans(@RequestParam(name="status",required=false)String status){return ApiResponse.success(service.plans(status));}
 @GetMapping("/executions") public ApiResponse<List<VaccinationDtos.ExecutionItem>> executions(@RequestParam(name="planId",required=false)String planId){return ApiResponse.success(service.executions(planId));}
 @PostMapping("/plans") @SaCheckRole(value={"ADMIN","FARM_MANAGER","VET"},mode=SaMode.OR)
 public ApiResponse<VaccinationDtos.PlanItem> createPlan(@RequestHeader("X-Idempotency-Key") @NotBlank String key,@Valid @RequestBody VaccinationDtos.CreatePlan r){return ApiResponse.success(service.createPlan(r,key));}
 @PostMapping("/executions") @SaCheckRole(value={"ADMIN","FARM_MANAGER","VET","WORKER"},mode=SaMode.OR)
 public ApiResponse<VaccinationDtos.ExecutionResult> execute(@RequestHeader("X-Idempotency-Key") @NotBlank String key,@Valid @RequestBody VaccinationDtos.CreateExecution r){return ApiResponse.success(service.execute(r,key));}
 @PostMapping("/plans/{id}/cancel")@SaCheckRole(value={"ADMIN","FARM_MANAGER","VET"},mode=SaMode.OR)public ApiResponse<CorrectionResult>cancelPlan(@PathVariable long id,@RequestHeader("X-Idempotency-Key")@NotBlank String key,@Valid@RequestBody VoidBusinessRequest r){return ApiResponse.success(corrections.cancelVaccinationPlan(id,r,key));}
 @PostMapping("/executions/{id}/void")@SaCheckRole(value={"ADMIN","FARM_MANAGER","VET"},mode=SaMode.OR)public ApiResponse<CorrectionResult>voidExecution(@PathVariable long id,@RequestHeader("X-Idempotency-Key")@NotBlank String key,@Valid@RequestBody VoidBusinessRequest r){return ApiResponse.success(corrections.voidVaccinationExecution(id,r,key));}
}
