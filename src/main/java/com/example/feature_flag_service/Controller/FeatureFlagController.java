package com.example.feature_flag_service.Controller;

import com.example.feature_flag_service.DTO.FlagRequest;
import com.example.feature_flag_service.DTO.FlagResponse;
import com.example.feature_flag_service.Service.FeatureFlagService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/flags")
public class FeatureFlagController {
    private final FeatureFlagService service;

    public FeatureFlagController(FeatureFlagService service) {
        this.service = service;
    }

    @PostMapping
    public FlagResponse create(@RequestHeader("X-Tenant-ID") String tenantId,
                               @Valid @RequestBody FlagRequest req) {
        return service.create(tenantId, req);
    }

    @GetMapping
    public List<FlagResponse> list(@RequestHeader("X-Tenant-ID") String tenantId) {
        return service.list(tenantId);
    }

    @GetMapping("/{id}")
    public FlagResponse get(@RequestHeader("X-Tenant-ID") String tenantId, @PathVariable UUID id) {
        return service.get(tenantId, id);
    }

    @PutMapping("/{id}")
    public FlagResponse update(@RequestHeader("X-Tenant-ID") String tenantId,
                               @PathVariable UUID id, @Valid @RequestBody FlagRequest req) {
        return service.update(tenantId, id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestHeader("X-Tenant-ID") String tenantId, @PathVariable UUID id) {
        service.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }

}