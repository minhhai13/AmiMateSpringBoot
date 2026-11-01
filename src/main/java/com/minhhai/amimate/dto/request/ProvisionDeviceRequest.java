package com.minhhai.amimate.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProvisionDeviceRequest(
        @NotBlank @Pattern(regexp = "^\\d{1,20}$", message = "agentId Xiaozhi phải là số") String agentId,
        @Size(max = 100) String name
) {}
