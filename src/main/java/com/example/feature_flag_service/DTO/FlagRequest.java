package com.example.feature_flag_service.DTO;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.Set;

public record FlagRequest(
        @NotBlank(message = "Flag name is required") String name,
        boolean enabled,
        @Min(0) @Max(100) int rolloutPercentage,
        Set<String> targetedUsers
) {}