package com.example.feature_flag_service;

import com.example.feature_flag_service.DTO.FlagRequest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FeatureFlagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    @Test
    void createAndFetchFlag() throws Exception {
        String body = objectMapper.writeValueAsString(new FlagRequest("dark_mode", true));

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
                        .content(objectMapper.writeValueAsString(new FlagRequest("beta_feature", true))))
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

    // --- The most important test: cross-tenant isolation ---

    @Test
    void tenantCannotReadAnotherTenantsFlag() throws Exception {
        UUID id = createFlag(TENANT_A, "secret_flag", true);

        mockMvc.perform(get("/flags/" + id).header("X-Tenant-ID", TENANT_B))
                .andExpect(status().isNotFound());
    }

    @Test
    void tenantCannotEvalAnotherTenantsFlagAsOn() throws Exception {
        createFlag(TENANT_A, "secret_flag", true);

        // Tenant B evaluating a same-named flag it never created must get "off",
        // never leaking Tenant A's state
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

        // still exists for the real owner
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

    private UUID createFlag(String tenant, String name, boolean enabled) throws Exception {
        String response = mockMvc.perform(post("/flags")
                        .header("X-Tenant-ID", tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FlagRequest(name, enabled))))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }
}