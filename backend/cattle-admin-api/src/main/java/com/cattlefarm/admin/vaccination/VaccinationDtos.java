package com.cattlefarm.admin.vaccination;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;import java.time.*;import java.util.List;
public final class VaccinationDtos {private VaccinationDtos(){}
 public record Target(@NotBlank @Pattern(regexp="HERD|BARN|CATTLE") String targetType,@NotBlank String targetObjectId){}
 public record CreatePlan(@NotBlank @Size(max=100) String planName,@NotBlank @Size(max=100) String vaccineItem,@NotNull LocalDate planDate,@NotNull LocalDate dueDate,@Size(max=500) String remark,@NotEmpty @Valid List<Target> targets){}
 public record CattleExecution(@NotBlank String cattleId,@Size(max=255) String reaction){}
 public record CreateExecution(String planId,@NotNull LocalDateTime executionDate,@NotBlank @Size(max=100) String vaccineItem,@Size(max=100) String batchNo,@Size(max=500) String remark,@NotEmpty @Valid List<CattleExecution> cattle){}
 public record PlanItem(String planId,String planName,String vaccineItem,LocalDate planDate,LocalDate dueDate,String status,String targetSummary,long targetCount,long executedCount,String remark,int version){}
 public record ExecutionResult(String executionId,String planId,int cattleCount,String planStatus){}
 public record ExecutionItem(String executionId,String planId,String planName,LocalDateTime executionDate,String vaccineItem,String batchNo,String executorName,String remark,long cattleCount,String cattleSummary,int version){}
}
