package com.minhhai.amimate.dto.response;

public record DeviceProvisionedResponse(
        String deviceId,
        String name,
        String pinCode
) {}
