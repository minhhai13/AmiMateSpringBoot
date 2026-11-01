package com.minhhai.amimate.controller;

import com.minhhai.amimate.dto.request.ProvisionDeviceRequest;
import com.minhhai.amimate.dto.response.DeviceProvisionedResponse;
import com.minhhai.amimate.dto.request.ClaimDeviceRequest;
import com.minhhai.amimate.dto.response.DeviceResponse;
import com.minhhai.amimate.exception.ApiException;
import com.minhhai.amimate.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {
    private final DeviceService deviceService;

    @Value("${amimate.admin.secret}")
    private String adminSecret;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping("/provision")
    public ResponseEntity<DeviceProvisionedResponse> provision(
            @RequestHeader(value = "X-Admin-Secret", required = false) String secretHeader,
            @Valid @RequestBody ProvisionDeviceRequest req) {

        if (secretHeader == null || !MessageDigest.isEqual(
                secretHeader.getBytes(StandardCharsets.UTF_8),
                adminSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Sai Admin Secret. Từ chối truy cập!");
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(deviceService.provision(req.agentId(), req.name()));
    }

    @PostMapping("/claim")
    public ResponseEntity<DeviceResponse> claimDevice(@AuthenticationPrincipal Long userId,
                                                      @Valid @RequestBody ClaimDeviceRequest req) {
        return ResponseEntity.status(HttpStatus.OK).body(deviceService.claimDevice(userId, req));
    }

    @GetMapping
    public List<DeviceResponse> myDevices(@AuthenticationPrincipal Long userId) {
        return deviceService.listMine(userId);
    }

}