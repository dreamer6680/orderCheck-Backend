package com.packflow.app.security;

import com.packflow.app.support.PostgresIntegrationTest;
import com.packflow.app.admin.AdminDtos;
import com.packflow.app.admin.AdminUserService;
import com.packflow.app.user.AppUserRepository;
import com.packflow.app.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthApiTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AdminUserService adminUsers;
    @Autowired
    private AppUserRepository users;

    @Test
    void logsInEachDemoUserWithTheMatchingRole() throws Exception {
        assertLogin("sales", "sales123", "Sales", "SALES");
        assertLogin("warehouse", "warehouse123", "Warehouse", "WAREHOUSE");
        assertLogin("manager", "manager123", "Manager", "MANAGER");
    }

    @Test
    void rejectsInvalidCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"sales\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requiresATokenForCurrentUser() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requiresAuthenticationForOtherApiEndpoints() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyPermitsPostRequestsToLogin() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void permitsHealthChecksWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void returnsTheAuthenticatedUserForCurrentUser() throws Exception {
        String token = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"manager\",\"password\":\"manager123\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("manager"))
                .andExpect(jsonPath("$.displayName").value("Manager"))
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }

    @Test
    void passwordResetRevokesExistingTokenEvenForReadOnlyEndpoints() throws Exception {
        String token = loginToken("warehouse", "warehouse123");
        Long warehouseId = users.findByUsername("warehouse").orElseThrow().getId();

        mockMvc.perform(get("/api/products").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        adminUsers.resetPassword(warehouseId, "A new secure password 456!", "manager");
        mockMvc.perform(get("/api/products").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disablingOrChangingRoleRevokesExistingTokenEvenForReadOnlyEndpoints() throws Exception {
        Long warehouseId = users.findByUsername("warehouse").orElseThrow().getId();
        String token = loginToken("warehouse", "warehouse123");
        adminUsers.update(warehouseId, new AdminDtos.UpdateUserRequest(
                "Warehouse", Role.SALES, true), "manager");
        mockMvc.perform(get("/api/products").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        String nextToken = loginToken("warehouse", "warehouse123");
        adminUsers.update(warehouseId, new AdminDtos.UpdateUserRequest(
                "Warehouse", Role.SALES, false), "manager");
        mockMvc.perform(get("/api/products").header("Authorization", "Bearer " + nextToken))
                .andExpect(status().isUnauthorized());
    }

    private String loginToken(String username, String password) throws Exception {
        String body = """
                {"username":"%s","password":"%s"}
                """.formatted(username, password);
        String payload = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(payload).get("token").asText();
    }

    private void assertLogin(String username, String password, String displayName, String role) throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.id").isNumber())
                .andExpect(jsonPath("$.user.username").value(username))
                .andExpect(jsonPath("$.user.displayName").value(displayName))
                .andExpect(jsonPath("$.user.role").value(role));
    }
}
