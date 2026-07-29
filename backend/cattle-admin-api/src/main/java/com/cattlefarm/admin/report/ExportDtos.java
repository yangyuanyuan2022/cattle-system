package com.cattlefarm.admin.report;
import jakarta.validation.constraints.*;import java.time.*;
public final class ExportDtos{private ExportDtos(){}public record Create(@NotNull LocalDate startDate,@NotNull LocalDate endDate){}public record Item(String exportId,String module,String status,String fileName,Integer rowCount,String failReason,LocalDateTime expiredAt,LocalDateTime createdAt){} }
