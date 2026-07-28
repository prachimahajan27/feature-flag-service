package com.example.feature_flag_service.Service;

import com.example.feature_flag_service.DTO.EvalResponse;
import com.example.feature_flag_service.DTO.FlagRequest;
import com.example.feature_flag_service.DTO.FlagResponse;
import com.example.feature_flag_service.Repository.FeatureFlagRepository;
import com.example.feature_flag_service.entity.FeatureFlag;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class FeatureFlagService {
    private final FeatureFlagRepository repo;

    public FeatureFlagService(FeatureFlagRepository repo) {
        this.repo = repo;
    }

    public FlagResponse create(String tenantId, FlagRequest req) {
        repo.findByNameAndTenantId(req.name(), tenantId).ifPresent(f -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Flag already exists");
        });
        FeatureFlag flag = new FeatureFlag();
        flag.setTenantId(tenantId);
        flag.setName(req.name());
        flag.setEnabled(req.enabled());
        return FlagResponse.from(repo.save(flag));
    }

    public List<FlagResponse> list(String tenantId) {
        return repo.findAllByTenantId(tenantId).stream()
                .map(FlagResponse::from).toList();
    }

    public FlagResponse get(String tenantId, UUID id) {
        return FlagResponse.from(findOrThrow(tenantId, id));
    }

    public FlagResponse update(String tenantId, UUID id, FlagRequest req) {
        FeatureFlag flag = findOrThrow(tenantId, id);
        flag.setEnabled(req.enabled());
        flag.setUpdatedAt(Instant.now());
        return FlagResponse.from(repo.save(flag));
    }

    public void delete(String tenantId, UUID id) {
        findOrThrow(tenantId, id); // 404 if not yours, don't leak existence
        repo.deleteByIdAndTenantId(id, tenantId);
    }

    private FeatureFlag findOrThrow(String tenantId, UUID id) {
        return repo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flag not found"));
    }
    public EvalResponse evaluate(String tenantId, String flagName, String user) {
        boolean on = repo.findByNameAndTenantId(flagName, tenantId)
                .map(FeatureFlag::isEnabled)
                .orElse(false); // unknown flag = off, don't error

        return new EvalResponse(flagName, user, on);
    }
}
