package com.minhhai.amimate.service;

import com.minhhai.amimate.dto.request.ClaimDeviceRequest;
import com.minhhai.amimate.dto.response.DeviceProvisionedResponse;
import com.minhhai.amimate.dto.response.DeviceResponse;
import com.minhhai.amimate.entity.Device;
import com.minhhai.amimate.exception.ApiException;
import com.minhhai.amimate.repository.DeviceRepository;
import com.minhhai.amimate.security.ApiKeys;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class DeviceService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DeviceRepository devices;
    private final ClaimAttemptLimiter claimLimiter;

    public DeviceService(DeviceRepository devices, ClaimAttemptLimiter claimLimiter) {
        this.devices = devices;
        this.claimLimiter = claimLimiter;
    }

    /**
     * Đăng ký robot đã được nạp firmware từ Xiaozhi. deviceId chính là agentId của Xiaozhi;
     * PIN do BE sinh và chỉ trả về một lần để in lên hộp.
     */
    @Transactional
    public DeviceProvisionedResponse provision(String agentId, String name) {
        if (devices.findByDeviceId(agentId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Robot này đã được đăng ký");
        }

        String rawPin = String.format("%06d", RANDOM.nextInt(1_000_000));

        Device device = new Device();
        device.setDeviceId(agentId);
        device.setName(name != null && !name.isBlank() ? name : "AmiMate Robot");
        device.setPinHash(ApiKeys.hash(rawPin));
        devices.save(device);

        return new DeviceProvisionedResponse(agentId, device.getName(), rawPin);
    }

    @Transactional
    public DeviceResponse claimDevice(Long ownerId, ClaimDeviceRequest req) {
        claimLimiter.checkAllowed(req.deviceId());

        Device device = devices.findByDeviceId(req.deviceId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy thiết bị này trong hệ thống"));

        if (device.getOwnerId() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "Robot này đã được liên kết với tài khoản khác");
        }

        boolean pinOk = MessageDigest.isEqual(
                ApiKeys.hash(req.pinCode()).getBytes(StandardCharsets.UTF_8),
                device.getPinHash().getBytes(StandardCharsets.UTF_8));
        if (!pinOk) {
            claimLimiter.recordFailure(req.deviceId());
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mã PIN xác thực không chính xác");
        }

        claimLimiter.recordSuccess(req.deviceId());
        device.setOwnerId(ownerId);
        return DeviceResponse.from(devices.save(device));
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
