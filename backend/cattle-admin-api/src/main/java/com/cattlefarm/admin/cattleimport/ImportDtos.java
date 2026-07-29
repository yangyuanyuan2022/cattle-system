package com.cattlefarm.admin.cattleimport;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;

public final class ImportDtos {
    private ImportDtos() {}
    public record Confirm(@NotBlank String importId) {}
    public record Result(String importId,String status,int totalCount,int successCount,int failCount,List<ErrorItem> errors) {}
    public record LogItem(String importId,String module,String fileName,String status,int totalCount,int successCount,int failCount,String errorSummary,String operatorId,LocalDateTime createdAt) {}
    public record ErrorItem(String errorId,int rowNo,String fieldName,String rawValue,String errorCode,String errorMessage) {}
}
