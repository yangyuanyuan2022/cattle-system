package com.cattlefarm.admin.correction;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VoidBusinessRequest(
        @NotBlank @Size(max = 255) String reason,
        @NotNull @Min(0) Integer version
) {
}
