package com.cattlefarm.admin.auth;

import jakarta.validation.constraints.NotBlank;

public record MiniappBindRequest(
        @NotBlank String code,
        @NotBlank String username,
        @NotBlank String password
) {
}
