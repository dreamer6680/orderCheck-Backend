package com.packflow.app.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packflow.app.product.Product;
import com.packflow.app.product.ProductRepository;
import com.packflow.app.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class InboundValidationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    private Long productId;

    @BeforeEach
    void createProduct() {
        productId = productRepository.save(new Product(
                "VALIDATION-" + System.nanoTime(), "Validation product", "kg", BigDecimal.ZERO, true)).getId();
    }

    @Test
    @WithMockUser(username = "warehouse", roles = "WAREHOUSE")
    void rejectsQuantitiesOutsideTheNumericEighteenThreePrecision() throws Exception {
        postInbound(new BigDecimal("0.0001"))
                .andExpect(status().isBadRequest());
        postInbound(new BigDecimal("1000000000000000.000"))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions postInbound(BigDecimal quantity) throws Exception {
        return mockMvc.perform(post("/api/inventory/inbounds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "productId", productId,
                        "quantity", quantity,
                        "remark", "validation"))));
    }
}
