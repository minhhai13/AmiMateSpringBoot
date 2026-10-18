package com.minhhai.amimate.service;

import com.minhhai.amimate.dto.request.ClaimDeviceRequest;
import com.minhhai.amimate.dto.response.DeviceResponse;
import com.minhhai.amimate.entity.Device;
import com.minhhai.amimate.exception.ApiException;
import com.minhhai.amimate.repository.DeviceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.minhhai.amimate.dto.response.DeviceGeneratedResponse;
import com.minhhai.amimate.security.ApiKeys;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import java.util.Random;

import java.util.List;

@Service
public class DeviceService {
    private final DeviceRepository devices;

    public DeviceService(DeviceRepository devices) {
        this.devices = devices;
    }

    @Transactional
    public DeviceGeneratedResponse generateDeviceForFactory(String deviceName) {
        String rawDeviceId = "AMI-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String rawApiKey = ApiKeys.generate();
        String rawPin = String.format("%06d", new Random().nextInt(999999));

        Device device = new Device();
        device.setDeviceId(rawDeviceId);
        device.setName(deviceName != null && !deviceName.isBlank() ? deviceName : "AmiMate Robot");
        
        // Chỉ lưu Hash xuống Database
        device.setApiKeyHash(ApiKeys.hash(rawApiKey));
        device.setPinHash(ApiKeys.hash(rawPin));
        
        devices.save(device);

        return new DeviceGeneratedResponse(rawDeviceId, device.getName(), rawApiKey, rawPin);
    }
    
    @Transactional
    public DeviceResponse claimDevice(Long ownerId, ClaimDeviceRequest req) {
        Device device = devices.findByDeviceId(req.deviceId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy thiết bị này trong hệ thống"));
                
        if (device.getOwnerId() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "Robot này đã được liên kết với tài khoản khác");
        }
        
        if (!ApiKeys.hash(req.pinCode()).equals(device.getPinHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mã PIN xác thực không chính xác");
        }
        
        device.setOwnerId(ownerId);
        Device saved = devices.save(device);
        return DeviceResponse.from(saved);
    }

    public List<DeviceResponse> listMine(Long ownerId) {
        return devices.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(DeviceResponse::from)
                .toList();
    }

    public void requireOwned(Long ownerId, String deviceId) {
        if (!devices.existsByDeviceIdAndOwnerId(deviceId, ownerId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy thiết bị hoặc bạn không có quyền truy cập");
        }
    }
}