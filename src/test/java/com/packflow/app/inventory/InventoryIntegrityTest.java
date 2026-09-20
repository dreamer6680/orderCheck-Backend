package com.packflow.app.inventory;

import com.packflow.app.product.Product;
import com.packflow.app.product.ProductRepository;
import com.packflow.app.support.PostgresIntegrationTest;
import com.packflow.app.user.AppUser;
import com.packflow.app.user.AppUserRepository;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryIntegrityTest extends PostgresIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InboundRecordRepository inboundRecordRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Product product;

    @BeforeEach
    void createProduct() {
        product = productRepository.save(new Product(
                "INTEGRITY-" + System.nanoTime(), "Integrity test product", "kg", BigDecimal.ZERO, true));
    }

    @Test
    void productCreationCreatesAZeroQuantityInventoryRow() {
        assertThat(inventoryRepository.findByProductId(product.getId()).orElseThrow().getQuantity())
                .isEqualByComparingTo("0.000");
    }

    @Test
    void inboundIntentRejectsMissingOrNonPositiveRecords() {
        Inventory inventory = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        AppUser warehouse = appUserRepository.findByUsername("warehouse").orElseThrow();

        assertThatThrownBy(() -> inventory.recordInbound(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inventory.recordInbound(new InboundRecord(
                "invalid", product, BigDecimal.ZERO, "", warehouse)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rollsBackInboundRecordWhenPhysicalStockUpdateCannotPersist() {
        jdbcTemplate.update("UPDATE inventory SET quantity = ? WHERE product_id = ?",
                new BigDecimal("999999999999999.000"), product.getId());

        assertThatThrownBy(() -> inventoryService.recordInbound(
                product.getId(), new BigDecimal("1.000"), "overflow", "warehouse"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(inboundRecordRepository.findByProductId(product.getId())).isEmpty();
        assertThat(inventoryRepository.findByProductId(product.getId()).orElseThrow().getQuantity())
                .isEqualByComparingTo("999999999999999.000");
    }

    @Test
    void concurrentInboundTransactionsKeepBothRecordsAndBothQuantities() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> recordWhenReleased(ready, start, new BigDecimal("12.500")));
            var second = executor.submit(() -> recordWhenReleased(ready, start, new BigDecimal("7.500")));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(inventoryRepository.findByProductId(product.getId()).orElseThrow().getQuantity())
                .isEqualByComparingTo("20.000");
        assertThat(inboundRecordRepository.findByProductId(product.getId())).hasSize(2);
    }

    @Test
    void inventoryProjectionUsesZeroPendingQuantityWhenNoOutboundRecordsExist() {
        InventoryService.InventoryProjection projection = inventoryService.inventoryForProduct(product.getId());

        assertThat(projection.physicalQuantity()).isEqualByComparingTo("0.000");
        assertThat(projection.pendingQuantity()).isEqualByComparingTo("0.000");
        assertThat(projection.availableQuantity()).isEqualByComparingTo("0.000");
    }

    @Test
    void inventoryProjectionSubtractsPendingOutboundQuantityFromPhysicalQuantity() {
        inventoryService.recordInbound(product.getId(), new BigDecimal("12.500"), "arrival", "warehouse");
        Long orderId = jdbcTemplate.queryForObject(
                "INSERT INTO sales_order (order_no, customer_name, status, created_by) "
                        + "VALUES (?, ?, 'PENDING_OUTBOUND', (SELECT id FROM app_user WHERE username = 'sales')) RETURNING id",
                Long.class, "ORDER-" + System.nanoTime(), "Projection customer");
        Long orderItemId = jdbcTemplate.queryForObject(
                "INSERT INTO sales_order_item (order_id, product_id, ordered_quantity) VALUES (?, ?, ?) RETURNING id",
                Long.class, orderId, product.getId(), new BigDecimal("4.250"));
        jdbcTemplate.update("INSERT INTO outbound_record "
                        + "(record_no, order_id, order_item_id, product_id, planned_quantity, planned_outbound_date, status) "
                        + "VALUES (?, ?, ?, ?, ?, CURRENT_DATE, 'PENDING'",
                "OUT-" + System.nanoTime(), orderId, orderItemId, product.getId(), new BigDecimal("4.250"));

        InventoryService.InventoryProjection projection = inventoryService.inventoryForProduct(product.getId());

        assertThat(projection.physicalQuantity()).isEqualByComparingTo("12.500");
        assertThat(projection.pendingQuantity()).isEqualByComparingTo("4.250");
        assertThat(projection.availableQuantity()).isEqualByComparingTo("8.250");
    }

    private InventoryService.InboundResult recordWhenReleased(
            CountDownLatch ready, CountDownLatch start, BigDecimal quantity) throws InterruptedException {
        ready.countDown();
        start.await();
        return inventoryService.recordInbound(product.getId(), quantity, "concurrent", "warehouse");
    }
}
