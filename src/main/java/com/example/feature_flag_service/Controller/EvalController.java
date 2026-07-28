package com.example.feature_flag_service.Controller;

import com.example.feature_flag_service.DTO.EvalResponse;
import com.example.feature_flag_service.Service.FeatureFlagService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EvalController {
    private final FeatureFlagService service;

    public EvalController(FeatureFlagService service) {
        this.service = service;
    }

    @GetMapping("/eval")
    public EvalResponse eval(@RequestHeader("X-Tenant-ID") String tenantId,
                             @RequestParam String flag,
                             @RequestParam String user) {
        return service.evaluate(tenantId, flag, user);
    }
}