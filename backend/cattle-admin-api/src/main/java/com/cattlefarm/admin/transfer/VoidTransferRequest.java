package com.cattlefarm.admin.transfer;import jakarta.validation.constraints.*;public record VoidTransferRequest(@NotBlank@Size(max=255)String reason,@NotNull Integer cattleVersion){}
