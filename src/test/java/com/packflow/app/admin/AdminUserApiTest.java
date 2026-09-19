package com.packflow.app.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.packflow.app.support.PostgresIntegrationTest;
import com.packflow.app.user.AppUserRepository;
import com.packflow.app.user.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminUserApiTest extends PostgresIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private AppUserRepository users;
    @Autowired private PasswordEncoder passwords;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("delete from app_user where username like 'admintest-%'");
        jdbc.update("update app_user set display_name = 'Manager', role = 'MANAGER', enabled = true "
                + "where username = 'manager'");
    }

    @Test
    @WithMockUser(username = "manager", roles = "MANAGER")
    void managerCanListFixedRolesWithDescriptions() throws Exception {
        mvc.perform(get("/api/admin/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].role").value("SALES"))
                .andExpect(jsonPath("$[0].description").isNotEmpty())
                .andExpect(jsonPath("$[2].role").value("MANAGER"));
    }

    @Test
    @WithMockUser(username = "manager", roles = "MANAGER")
    void managerCreatesAndReadsUserWithoutExposingPasswordHash() throws Exception {
        String username = unique("create");
        JsonNode created = json.readTree(mvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(username, "Initial Pass 123!", "New Sales", "SALES")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("SALES"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString());

        var stored = users.findByUsername(username).orElseThrow();
        assertThat(stored.getPasswordHash()).startsWith("$2");
        assertThat(passwords.matches("Initial Pass 123!", stored.getPasswordHash())).isTrue();

        mvc.perform(get("/api/admin/users/{id}", created.get("id").asLong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("New Sales"));
        mvc.perform(get("/api/admin/users").param("keyword", username).param("role", "SALES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value(username));
    }

    @Test
    @WithMockUser(username = "manager", roles = "MANAGER")
    void duplicateUsernameIsRejectedCaseInsensitively() throws Exception {
        String username = unique("duplicate");
        create(username, "SALES");

        mvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(username.toUpperCase(), "Another Pass 123!", "Duplicate", "WAREHOUSE")))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "manager", roles = "MANAGER")
    void managerUpdatesProfileRoleAndStatusThenResetsPassword() throws Exception {
        long id = create(unique("update"), "SALES");

        mvc.perform(put("/api/admin/users/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Warehouse Operator","role":"WAREHOUSE","enabled":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Warehouse Operator"))
                .andExpect(jsonPath("$.role").value("WAREHOUSE"))
                .andExpect(jsonPath("$.enabled").value(false));

        mvc.perform(post("/api/admin/users/{id}/reset-password", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"Replacement Pass 456!\"}"))
                .andExpect(status().isNoContent());
        assertThat(passwords.matches("Replacement Pass 456!", users.findById(id).orElseThrow().getPasswordHash()))
                .isTrue();
    }

    @Test
    @WithMockUser(username = "manager", roles = "MANAGER")
    void managerCannotDisableOrDemoteSelf() throws Exception {
        long id = users.findByUsername("manager").orElseThrow().getId();
        mvc.perform(put("/api/admin/users/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Manager\",\"role\":\"MANAGER\",\"enabled\":false}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/admin/users/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Manager\",\"role\":\"SALES\",\"enabled\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "manager", roles = "MANAGER")
    void serviceRejectsStaleManagerAuthorityFromToken() throws Exception {
        jdbc.update("update app_user set role = 'SALES' where username = 'manager'");
        mvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());

        jdbc.update("update app_user set role = 'MANAGER', enabled = false where username = 'manager'");
        mvc.perform(get("/api/admin/roles")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "warehouse", roles = "WAREHOUSE")
    void warehouseCannotUseAdminApi() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/roles")).andExpect(status().isForbidden());
    }

    private long create(String username, String role) throws Exception {
        String content = mvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(username, "Initial Pass 123!", "Created User", role)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(content).get("id").asLong();
    }

    private String createBody(String username, String password, String displayName, String role) throws Exception {
        return json.writeValueAsString(new CreatePayload(username, password, displayName, Role.valueOf(role), true));
    }

    private String unique(String suffix) {
        return "admintest-" + suffix + "-" + System.nanoTime();
    }

    private record CreatePayload(String username, String password, String displayName, Role role, boolean enabled) { }
}
