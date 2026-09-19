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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class InboundServiceTest extends PostgresIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InboundRecordRepository inboundRecordRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Long productId;

    @BeforeEach
    void createProduct() {
        Product product = productRepository.save(new Product(
                "INBOUND-" + System.nanoTime(), "Inbound test product", "kg", BigDecimal.ZERO, true));
        productId = product.getId();
    }

    @Test
    void recordsInboundAndIncrementsPhysicalQuantityTogether() {
        InventoryService.InboundResult result = inventoryService.recordInbound(
                productId, new BigDecimal("12.500"), "arrival", "warehouse");

        assertThat(result.quantity()).isEqualByComparingTo("12.500");
        assertThat(inventoryRepository.findByProductId(productId).orElseThrow().getQuantity())
                .isEqualByComparingTo("12.500");
        assertThat(inboundRecordRepository.findByProductId(productId)).singleElement()
                .extracting(InboundRecord::getQuantity)
                .isEqualTo(new BigDecimal("12.500"));
    }

    @Test
    @WithMockUser(username = "warehouse", roles = "WAREHOUSE")
    void warehouseCanCreateInboundRecord() throws Exception {
        postInbound(new BigDecimal("2.000"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "manager", roles = "MANAGER")
    void managerCanCreateInboundRecord() throws Exception {
        postInbound(new BigDecimal("3.000"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "sales", roles = "SALES")
    void salesCannotCreateInboundRecord() throws Exception {
        postInbound(new BigDecimal("1.000"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "warehouse", roles = "WAREHOUSE")
    void rejectsZeroOrNegativeInboundQuantity() throws Exception {
        postInbound(BigDecimal.ZERO)
                .andExpect(status().isBadRequest());
        postInbound(new BigDecimal("-1.000"))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions postInbound(BigDecimal quantity) throws Exception {
        return mockMvc.perform(post("/api/inventory/inbounds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "productId", productId,
                        "quantity", quantity,
                        "remark", "arrival"))));
    }
}
