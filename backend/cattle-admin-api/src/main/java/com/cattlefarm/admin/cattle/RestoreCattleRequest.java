package com.cattlefarm.admin.cattle;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RestoreCattleRequest(
        @NotBlank String reason,
        @NotNull @Min(0) Integer version
) {
}
