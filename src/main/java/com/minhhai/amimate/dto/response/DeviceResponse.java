package com.minhhai.amimate.dto.response;

import com.minhhai.amimate.entity.Device;

import java.time.Instant;

public record DeviceResponse(String deviceId, String name, Instant createdAt) {
    public static DeviceResponse from(Device d) {
        return new DeviceResponse(d.getDeviceId(), d.getName(), d.getCreatedAt());
    }
}