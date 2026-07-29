package com.example.feature_flag_service.Service;

import com.example.feature_flag_service.DTO.EvalResponse;
import com.example.feature_flag_service.DTO.FlagRequest;
import com.example.feature_flag_service.DTO.FlagResponse;
import com.example.feature_flag_service.Repository.FeatureFlagRepository;
import com.example.feature_flag_service.entity.FeatureFlag;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
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
        flag.setRolloutPercentage(req.rolloutPercentage());
        flag.setTargetedUsers(req.targetedUsers() != null ? req.targetedUsers() : Set.of());
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
        flag.setRolloutPercentage(req.rolloutPercentage());
        flag.setTargetedUsers(req.targetedUsers() != null ? req.targetedUsers() : Set.of());
        flag.setUpdatedAt(Instant.now());
        return FlagResponse.from(repo.save(flag));
    }

    @Transactional
    public void delete(String tenantId, UUID id) {
        findOrThrow(tenantId, id); // 404 if not yours, don't leak existence
        repo.deleteByIdAndTenantId(id, tenantId);
    }

    private FeatureFlag findOrThrow(String tenantId, UUID id) {
        return repo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flag not found"));
    }
    public EvalResponse evaluate(String tenantId, String flagName, String user) {
        FeatureFlag flag = repo.findByNameAndTenantId(flagName, tenantId).orElse(null);

        boolean on = false;
        if (flag != null && flag.isEnabled()) {
            if (flag.getTargetedUsers().contains(user)) {
                on = true; // explicit targeting always wins
            } else if (flag.getRolloutPercentage() >= 100) {
                on = true;
            } else if (flag.getRolloutPercentage() > 0) {
                on = stableBucket(flagName, user) < flag.getRolloutPercentage();
            }
            // else: rollout is 0 and not targeted -> stays off (safe default)
        }
        return new EvalResponse(flagName, user, on);
    }

    // Deterministic 0-99 bucket for (flag, user) — same inputs always give the same
// bucket, so a user's eval result never flips between calls just because
// rollout% stays the same.
    private int stableBucket(String flagName, String user) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((flagName + ":" + user).getBytes(StandardCharsets.UTF_8));
            int value = ((hash[0] & 0xFF) << 24) | ((hash[1] & 0xFF) << 16)
                    | ((hash[2] & 0xFF) << 8) | (hash[3] & 0xFF);
            return Math.abs(value) % 100;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 always available on JVM
        }
    }
}
