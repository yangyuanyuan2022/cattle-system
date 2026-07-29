package com.cattlefarm.admin.cattle;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateCattleRequest(
        @NotBlank @Size(max = 50) String earTagNo,
        @Size(max = 50) String name,
        LocalDate birthDate,
        String sireId,
        @Size(max = 100) String sireText,
        @Size(max = 500) String remark,
        @NotBlank @Size(max = 500) String changeReason,
        @NotNull @Min(0) Integer version
) {
}
