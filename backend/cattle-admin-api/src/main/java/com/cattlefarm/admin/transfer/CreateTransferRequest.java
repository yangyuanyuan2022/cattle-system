package com.cattlefarm.admin.transfer;
import jakarta.validation.constraints.*; import java.time.LocalDateTime;
public record CreateTransferRequest(@NotBlank String cattleId,@NotBlank String toBarnId,String toHerdId,
 @NotNull LocalDateTime transferDate,@NotBlank @Size(max=255) String reason,@NotNull Integer version) {}
