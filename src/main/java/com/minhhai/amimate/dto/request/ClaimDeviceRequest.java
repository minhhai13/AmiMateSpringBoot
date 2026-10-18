package com.minhhai.amimate.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ClaimDeviceRequest(
        @NotBlank String deviceId,
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "Mã PIN phải đúng 6 chữ số") String pinCode
) {}