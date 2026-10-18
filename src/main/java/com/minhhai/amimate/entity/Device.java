package com.minhhai.amimate.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "device",
        uniqueConstraints = {
                @UniqueConstraint(name = "UQ_device_device_id", columnNames = "device_id"),
                @UniqueConstraint(name = "UQ_device_api_key_hash", columnNames = "api_key_hash")
        },
        indexes = @Index(name = "IX_device_owner", columnList = "owner_id"))
@Getter
@Setter
@NoArgsConstructor
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, updatable = false, length = 50)
    private String deviceId;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "api_key_hash", nullable = false, length = 64)
    private String apiKeyHash;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "pin_hash", nullable = false, length = 64)
    private String pinHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}