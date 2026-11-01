package com.minhhai.amimate.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ClaimDeviceRequest(
        @NotBlank @Pattern(regexp = "^\\d{1,20}$", message = "ID robot phải là số") String deviceId,
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "Mã PIN phải đúng 6 chữ số") String pinCode
) {}
