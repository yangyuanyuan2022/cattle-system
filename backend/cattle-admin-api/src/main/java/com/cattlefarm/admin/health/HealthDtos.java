package com.cattlefarm.admin.health;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.math.BigDecimal; import java.time.*; import java.util.List;
public final class HealthDtos { private HealthDtos(){}
 public record CreateCase(@NotBlank String cattleId,@NotNull LocalDateTime discoverDate,@NotBlank @Size(max=500) String symptom,@NotBlank @Pattern(regexp="NORMAL|SERIOUS|URGENT") String severity){}
 public record Medication(@NotBlank @Size(max=100) String medicineName,@DecimalMin("0.01") BigDecimal dosage,@Size(max=20) String unit,@Size(max=100) String usageMethod,@Min(0) Integer withdrawalDays,@Size(max=500) String remark){}
 public record CreateTreatment(@NotBlank String caseId,@NotNull LocalDateTime treatmentDate,@NotBlank @Size(max=500) String diagnosis,@Size(max=1000) String treatmentPlan,boolean needFollowUp,LocalDate followUpDate,@Valid List<Medication> medications){}
 public record CreateFollowUp(@NotBlank String caseId,@NotNull LocalDateTime followUpDate,@NotBlank @Pattern(regexp="CONTINUE_TREATMENT|RECOVERED|OBSERVE") String result,@Size(max=1000) String description){}
 public record CaseItem(String caseId,String caseNo,String cattleId,String earTagNo,String cattleName,LocalDateTime discoverDate,String symptom,String severity,String caseStatus,String healthStatus,long treatmentCount,LocalDate withdrawalUntil,int version){}
 public record TreatmentItem(String treatmentId,LocalDateTime treatmentDate,String diagnosis,String treatmentPlan,boolean needFollowUp,LocalDate followUpDate,String vetName,int version){}
 public record FollowUpItem(String followUpId,LocalDateTime followUpDate,String result,String description,String operatorName,int version){}
 public record CaseDetail(CaseItem caseInfo,List<TreatmentItem> treatments,List<FollowUpItem> followUps){}
 public record ActionResult(String businessId,String caseId,String cattleId,String caseStatus,String healthStatus,int cattleVersion,LocalDate withdrawalUntil){}
}
