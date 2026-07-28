package com.example.feature_flag_service.DTO;

import com.example.feature_flag_service.entity.FeatureFlag;

import java.time.Instant;
import java.util.UUID;

public record FlagResponse(UUID id, String name, boolean enabled, Instant updatedAt) {
    public static FlagResponse from(FeatureFlag f) {
        return new FlagResponse(f.getId(), f.getName(), f.isEnabled(), f.getUpdatedAt());
    }
}