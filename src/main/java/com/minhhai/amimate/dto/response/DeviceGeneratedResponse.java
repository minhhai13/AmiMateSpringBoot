package com.minhhai.amimate.dto.response;

public record DeviceGeneratedResponse(
        String deviceId, 
        String name, 
        String apiKey, 
        String pinCode
) {}