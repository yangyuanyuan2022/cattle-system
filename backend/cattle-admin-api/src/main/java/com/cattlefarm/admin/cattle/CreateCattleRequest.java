package com.cattlefarm.admin.cattle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateCattleRequest(
        @NotBlank @Size(max = 50) String earTagNo,
        @Size(max = 50) String name,
        @NotBlank @Pattern(regexp = "MALE|FEMALE") String sex,
        String breedId,
        LocalDate birthDate,
        @NotBlank @Pattern(regexp = "BIRTH|PURCHASE") String sourceType,
        @NotNull LocalDate entryDate,
        @NotBlank @Pattern(regexp = "CALF|GROWING|RESERVE|COW|BULL") String lifecycleStage,
        String herdId,
        String barnId,
        String sireId,
        @Size(max = 100) String sireText,
        @Size(max = 500) String remark
) {
}
