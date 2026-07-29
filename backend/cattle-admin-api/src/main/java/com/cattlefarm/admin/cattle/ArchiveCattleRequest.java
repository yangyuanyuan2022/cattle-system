package com.cattlefarm.admin.cattle;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record ArchiveCattleRequest(
        @NotBlank @Pattern(regexp = "SALE|DEATH|CULL|OTHER") String exitType,
        @NotNull LocalDate exitDate,
        @NotBlank String reason,
        boolean treatingRiskConfirmed,
        @NotNull @Min(0) Integer version
) {
}
