package com.packflow.app.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packflow.app.inventory.InventoryRepository;
import com.packflow.app.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ProductMaintenanceAuthorizationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void restoreManager() {
        jdbcTemplate.update("UPDATE app_user SET role = 'MANAGER', enabled = TRUE WHERE username = 'manager'");
    }

    @Test
    @WithMockUser(username = "manager", roles = "MANAGER")
    void currentPersistedManagerCanCreateAProductWithZeroInventory() throws Exception {
        String response = createProduct("manager")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long productId = objectMapper.readTree(response).get("id").longValue();
        assertThat(inventoryRepository.findByProductId(productId).orElseThrow().getQuantity())
                .isEqualByComparingTo("0.000");
    }

    @Test
    @WithMockUser(username = "warehouse", roles = "WAREHOUSE")
    void warehouseTokenCannotCreateProducts() throws Exception {
        createProduct("warehouse")
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "sales", roles = "SALES")
    void salesTokenCannotCreateProducts() throws Exception {
        createProduct("sales")
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "manager", roles = "MANAGER")
    void staleManagerTokenIsRejectedAfterPersistedRoleDowngrade() throws Exception {
        jdbcTemplate.update("UPDATE app_user SET role = 'WAREHOUSE' WHERE username = 'manager'");

        createProduct("stale-role")
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "manager", roles = "MANAGER")
    void staleManagerTokenIsRejectedAfterPersistedDisable() throws Exception {
        jdbcTemplate.update("UPDATE app_user SET enabled = FALSE WHERE username = 'manager'");

        createProduct("disabled-manager")
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions createProduct(String suffix) throws Exception {
        return mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "sku", "PRODUCT-" + suffix + "-" + System.nanoTime(),
                        "name", "Managed product",
                        "unit", "kg",
                        "safetyStock", new BigDecimal("1.000"),
                        "enabled", true))));
    }
}
