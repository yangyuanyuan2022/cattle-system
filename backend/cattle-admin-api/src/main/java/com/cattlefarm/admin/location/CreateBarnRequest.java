package com.cattlefarm.admin.location;
import jakarta.validation.constraints.*;
public record CreateBarnRequest(@NotBlank @Size(max=50) String barnCode,
 @NotBlank @Size(max=100) String barnName, @Size(max=30) String barnType,
 @Min(0) Integer capacity, @Size(max=500) String remark) {}
