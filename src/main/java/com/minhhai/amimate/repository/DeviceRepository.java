package com.minhhai.amimate.repository;
import com.minhhai.amimate.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    Optional<Device> findByApiKeyHash(String apiKeyHash);
    Optional<Device> findByDeviceId(String deviceId);
    boolean existsByDeviceIdAndOwnerId(String deviceId, Long ownerId);
    List<Device> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
}