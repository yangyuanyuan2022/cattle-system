package com.cattlefarm.admin.location;
import jakarta.validation.constraints.*;
public record CreateHerdRequest(@NotBlank @Size(max=50) String herdCode,
 @NotBlank @Size(max=100) String herdName, @Size(max=30) String herdType,
 String barnId, @Size(max=500) String remark) {}
