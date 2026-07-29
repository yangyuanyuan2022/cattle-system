package com.cattlefarm.admin.health;
import cn.dev33.satoken.annotation.*; import com.cattlefarm.common.api.ApiResponse; import com.cattlefarm.admin.correction.*; import jakarta.validation.Valid; import jakarta.validation.constraints.NotBlank; import org.springframework.web.bind.annotation.*; import java.util.List;
import java.util.Map;
@RestController @RequestMapping("/api/v1/health")
public class HealthController { private final HealthService service;private final BusinessCorrectionService corrections; public HealthController(HealthService service,BusinessCorrectionService corrections){this.service=service;this.corrections=corrections;}
 @GetMapping public ApiResponse<Map<String,String>> health(){return ApiResponse.success(Map.of("service","cattle-admin-api","status","UP"));}
 @GetMapping("/abnormalities") public ApiResponse<List<HealthDtos.CaseItem>> cases(@RequestParam(name="status",required=false) String status){return ApiResponse.success(service.cases(status));}
 @GetMapping("/cases/{caseId}") public ApiResponse<HealthDtos.CaseDetail> detail(@PathVariable("caseId") long id){return ApiResponse.success(service.caseDetail(id));}
 @PostMapping("/abnormalities") @SaCheckRole(value={"ADMIN","FARM_MANAGER","VET","WORKER"},mode=SaMode.OR)
 public ApiResponse<HealthDtos.ActionResult> report(@RequestHeader("X-Idempotency-Key") @NotBlank String key,@Valid @RequestBody HealthDtos.CreateCase r){return ApiResponse.success(service.report(r,key));}
 @PostMapping("/treatments") @SaCheckRole(value={"ADMIN","FARM_MANAGER","VET"},mode=SaMode.OR)
 public ApiResponse<HealthDtos.ActionResult> treatment(@RequestHeader("X-Idempotency-Key") @NotBlank String key,@Valid @RequestBody HealthDtos.CreateTreatment r){return ApiResponse.success(service.treatment(r,key));}
 @PostMapping("/follow-ups") @SaCheckRole(value={"ADMIN","FARM_MANAGER","VET"},mode=SaMode.OR)
 public ApiResponse<HealthDtos.ActionResult> followUp(@RequestHeader("X-Idempotency-Key") @NotBlank String key,@Valid @RequestBody HealthDtos.CreateFollowUp r){return ApiResponse.success(service.followUp(r,key));}
 @PostMapping("/cases/{id}/void")@SaCheckRole(value={"ADMIN","FARM_MANAGER","VET"},mode=SaMode.OR)public ApiResponse<CorrectionResult>voidCase(@PathVariable long id,@RequestHeader("X-Idempotency-Key")@NotBlank String key,@Valid@RequestBody VoidBusinessRequest r){return ApiResponse.success(corrections.voidHealth("CASE",id,r,key));}
 @PostMapping("/treatments/{id}/void")@SaCheckRole(value={"ADMIN","FARM_MANAGER","VET"},mode=SaMode.OR)public ApiResponse<CorrectionResult>voidTreatment(@PathVariable long id,@RequestHeader("X-Idempotency-Key")@NotBlank String key,@Valid@RequestBody VoidBusinessRequest r){return ApiResponse.success(corrections.voidHealth("TREATMENT",id,r,key));}
 @PostMapping("/follow-ups/{id}/void")@SaCheckRole(value={"ADMIN","FARM_MANAGER","VET"},mode=SaMode.OR)public ApiResponse<CorrectionResult>voidFollowUp(@PathVariable long id,@RequestHeader("X-Idempotency-Key")@NotBlank String key,@Valid@RequestBody VoidBusinessRequest r){return ApiResponse.success(corrections.voidHealth("FOLLOW_UP",id,r,key));}
}
