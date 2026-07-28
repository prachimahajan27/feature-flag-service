package com.example.feature_flag_service.Repository;

import com.example.feature_flag_service.entity.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {
    List<FeatureFlag> findAllByTenantId(String tenantId);
    Optional<FeatureFlag> findByIdAndTenantId(UUID id, String tenantId);
    Optional<FeatureFlag> findByNameAndTenantId(String name, String tenantId);
    void deleteByIdAndTenantId(UUID id, String tenantId);
}