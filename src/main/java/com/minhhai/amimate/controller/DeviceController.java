package com.minhhai.amimate.controller;

import com.minhhai.amimate.dto.response.DeviceGeneratedResponse;
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
import java.util.List;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {
    private final DeviceService deviceService;

    @Value("${amimate.factory.secret}")
    private String factorySecret;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping("/factory/generate")
    public ResponseEntity<DeviceGeneratedResponse> generateForFactory(
            @RequestHeader(value = "X-Factory-Secret", required = false) String secretHeader,
            @RequestParam(required = false) String name) {

        if (secretHeader == null || !secretHeader.equals(factorySecret)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Sai Factory Secret. Từ chối truy cập!");
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(deviceService.generateDeviceForFactory(name));
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