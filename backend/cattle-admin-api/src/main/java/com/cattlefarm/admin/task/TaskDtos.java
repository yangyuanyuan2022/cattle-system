package com.cattlefarm.admin.task;
import jakarta.validation.constraints.*;import java.time.*;import java.util.List;
public final class TaskDtos{private TaskDtos(){}
 public record Create(@NotBlank @Size(max=200)String title,@NotBlank @Pattern(regexp="INSPECTION|WEIGHT_RECORD|TRANSFER|OTHER")String taskType,String cattleId,String herdId,String barnId,String assigneeId,@NotNull LocalDate planDate,@NotNull LocalDate dueDate,@Pattern(regexp="NORMAL|IMPORTANT|URGENT")String priority){}
 public record Complete(@NotBlank @Size(max=1000)String result,@NotNull Integer version){}
 public record Reschedule(@NotNull LocalDate planDate,@NotNull LocalDate dueDate,@NotBlank @Size(max=255)String reason,@NotNull Integer version){}
 public record Cancel(@NotBlank @Size(max=255)String reason,@NotNull Integer version){}
 public record Item(String taskId,String sourceType,String sourceId,String taskType,String title,String cattleId,String earTagNo,LocalDate planDate,LocalDate dueDate,String priority,String status,String result,String assigneeName,int version){}
 public record Target(String targetType,String targetObjectId,String displayName){}
 public record Detail(Item task,List<Target> targets){}
}
