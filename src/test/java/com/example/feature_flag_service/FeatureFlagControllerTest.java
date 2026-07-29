package com.example.feature_flag_service;

import com.example.feature_flag_service.DTO.FlagRequest;
import com.example.feature_flag_service.Repository.FeatureFlagRepository;
import com.example.feature_flag_service.entity.FeatureFlag;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FeatureFlagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FeatureFlagRepository repo;

    @PersistenceContext
    private EntityManager entityManager;

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    @Test
    void createAndFetchFlag() throws Exception {
        String body = objectMapper.writeValueAsString(new FlagRequest("dark_mode", true, 100, Set.of(), null));

        String response = mockMvc.perform(post("/flags")
                        .header("X-Tenant-ID", TENANT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("dark_mode"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        mockMvc.perform(get("/flags/" + id).header("X-Tenant-ID", TENANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("dark_mode"));
    }

    @Test
    void updateFlag() throws Exception {
        UUID id = createFlag(TENANT_A, "beta_feature", false);

        mockMvc.perform(put("/flags/" + id)
                        .header("X-Tenant-ID", TENANT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FlagRequest("beta_feature", true, 100, Set.of(), 0L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void deleteFlag() throws Exception {
        UUID id = createFlag(TENANT_A, "temp_flag", true);

        mockMvc.perform(delete("/flags/" + id).header("X-Tenant-ID", TENANT_A))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/flags/" + id).header("X-Tenant-ID", TENANT_A))
                .andExpect(status().isNotFound());
    }

    @Test
    void evalReturnsOnWhenFlagEnabled() throws Exception {
        createFlag(TENANT_A, "new_checkout", true);

        mockMvc.perform(get("/eval")
                        .header("X-Tenant-ID", TENANT_A)
                        .param("flag", "new_checkout")
                        .param("user", "user-42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.on").value(true));
    }

    @Test
    void evalReturnsOffWhenFlagUnknown() throws Exception {
        mockMvc.perform(get("/eval")
                        .header("X-Tenant-ID", TENANT_A)
                        .param("flag", "nonexistent_flag")
                        .param("user", "user-42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.on").value(false));
    }

    // --- Cross-tenant isolation ---

    @Test
    void tenantCannotReadAnotherTenantsFlag() throws Exception {
        UUID id = createFlag(TENANT_A, "secret_flag", true);

        mockMvc.perform(get("/flags/" + id).header("X-Tenant-ID", TENANT_B))
                .andExpect(status().isNotFound());
    }

    @Test
    void tenantCannotEvalAnotherTenantsFlagAsOn() throws Exception {
        createFlag(TENANT_A, "secret_flag", true);

        mockMvc.perform(get("/eval")
                        .header("X-Tenant-ID", TENANT_B)
                        .param("flag", "secret_flag")
                        .param("user", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.on").value(false));
    }

    @Test
    void tenantCannotDeleteAnotherTenantsFlag() throws Exception {
        UUID id = createFlag(TENANT_A, "protected_flag", true);

        mockMvc.perform(delete("/flags/" + id).header("X-Tenant-ID", TENANT_B))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/flags/" + id).header("X-Tenant-ID", TENANT_A))
                .andExpect(status().isOk());
    }

    @Test
    void listOnlyReturnsOwnTenantsFlags() throws Exception {
        createFlag(TENANT_A, "flag_a", true);
        createFlag(TENANT_B, "flag_b", true);

        mockMvc.perform(get("/flags").header("X-Tenant-ID", TENANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("flag_a"));
    }

    // --- Optimistic locking ---

    @Test
    void concurrentUpdatesAreRejectedByOptimisticLock() throws Exception {
        UUID id = createFlag(TENANT_A, "concurrent_flag", true);
        entityManager.flush();
        entityManager.clear();

        FeatureFlag copyA = repo.findById(id).orElseThrow();
        entityManager.detach(copyA); // force copyB to be a separate instance, not the cached one

        FeatureFlag copyB = repo.findById(id).orElseThrow();
        entityManager.detach(copyB);

        copyA.setEnabled(false);
        repo.save(copyA);
        entityManager.flush(); // version increments in DB here

        copyB.setEnabled(true);
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            repo.save(copyB);
            entityManager.flush(); // stale version -> conflict surfaces on flush
        });
    }

    @Test
    void staleUpdateVersionReturns409() throws Exception {
        UUID id = createFlag(TENANT_A, "versioned_flag", false);

        FlagRequest firstEdit = new FlagRequest("versioned_flag", true, 100, Set.of(), 0L);
        mockMvc.perform(put("/flags/" + id)
                        .header("X-Tenant-ID", TENANT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstEdit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        FlagRequest staleEdit = new FlagRequest("versioned_flag", false, 0, Set.of(), 0L);
        mockMvc.perform(put("/flags/" + id)
                        .header("X-Tenant-ID", TENANT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(staleEdit)))
                .andExpect(status().isConflict());
    }

    // --- Validation ---

    @Test
    void createWithBlankNameReturns400() throws Exception {
        String body = objectMapper.writeValueAsString(new FlagRequest("", true, 0, Set.of(), null));

        mockMvc.perform(post("/flags")
                        .header("X-Tenant-ID", TENANT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // --- Rollout + targeting ---

    @Test
    void evalIsStableAcrossRepeatedCalls() throws Exception {
        createFlagWithRollout(TENANT_A, "beta", 50);

        String first = evalOnce(TENANT_A, "beta", "user-123");
        String second = evalOnce(TENANT_A, "beta", "user-123");

        assertEquals(first, second);
    }

    @Test
    void targetedUserIsOnRegardlessOfRollout() throws Exception {
        createFlagWithRolloutAndTargets(TENANT_A, "beta", 0, Set.of("vip-user"));

        mockMvc.perform(get("/eval").header("X-Tenant-ID", TENANT_A)
                        .param("flag", "beta").param("user", "vip-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.on").value(true));
    }

    @Test
    void zeroRolloutAndNoTargetDefaultsOff() throws Exception {
        createFlagWithRollout(TENANT_A, "beta", 0);

        mockMvc.perform(get("/eval").header("X-Tenant-ID", TENANT_A)
                        .param("flag", "beta").param("user", "random-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.on").value(false));
    }

    // --- Helpers ---

    private UUID createFlag(String tenant, String name, boolean enabled) throws Exception {
        String response = mockMvc.perform(post("/flags")
                        .header("X-Tenant-ID", tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FlagRequest(name, enabled, 100, Set.of(), null))))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID createFlagWithRollout(String tenant, String name, int rolloutPercentage) throws Exception {
        String response = mockMvc.perform(post("/flags")
                        .header("X-Tenant-ID", tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FlagRequest(name, true, rolloutPercentage, Set.of(), null))))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID createFlagWithRolloutAndTargets(String tenant, String name, int rolloutPercentage,
                                                 Set<String> targets) throws Exception {
        String response = mockMvc.perform(post("/flags")
                        .header("X-Tenant-ID", tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FlagRequest(name, true, rolloutPercentage, targets, null))))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private String evalOnce(String tenant, String flag, String user) throws Exception {
        return mockMvc.perform(get("/eval").header("X-Tenant-ID", tenant)
                        .param("flag", flag).param("user", user))
                .andReturn().getResponse().getContentAsString();
    }
}
