package com.packflow.app.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packflow.app.inventory.InventoryRepository;
import com.packflow.app.inventory.InventoryService;
import com.packflow.app.outbound.OutboundRecordRepository;
import com.packflow.app.outbound.OutboundStatus;
import com.packflow.app.product.Product;
import com.packflow.app.product.ProductRepository;
import com.packflow.app.product.ProductService;
import com.packflow.app.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.argThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OrderCheckingServiceTest extends PostgresIntegrationTest {
    @Autowired private OrderService orderService;
    @Autowired private SalesOrderRepository orderRepository;
    @MockitoSpyBean private OutboundRecordRepository outboundRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductService productService;
    @Autowired private InventoryService inventoryService;
    @MockitoSpyBean private InventoryRepository inventoryRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    private Product first;
    private Product second;

    @BeforeEach
    void stockProducts() {
        first = productRepository.save(new Product("BOX-" + System.nanoTime(), "Box", "箱", BigDecimal.ZERO, true));
        second = productRepository.save(new Product("BAG-" + System.nanoTime(), "Bag", "件", BigDecimal.ZERO, true));
        inventoryService.recordInbound(first.getId(), new BigDecimal("100.000"), null, "warehouse");
        inventoryService.recordInbound(second.getId(), new BigDecimal("70.000"), null, "warehouse");
    }

    @AfterEach
    void restoreUsers() {
        jdbc.update("update app_user set role = 'SALES', enabled = true where username = 'sales'");
        jdbc.update("update app_user set role = 'MANAGER', enabled = true where username = 'manager'");
    }

    @Test
    void createsMultipleItemsAndReturnsPersistedCreator() {
        var order = create(item(first, "20"), item(second, "30"));
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_CHECK);
        assertThat(order.items()).hasSize(2);
        assertThat(order.createdBy()).isEqualTo("sales");
        assertThat(orderService.getOrder(order.id(), "warehouse").items()).hasSize(2);
    }

    @Test
    void rejectsDuplicatesEmptyItemsInvalidQuantitiesAndDisabledProductsWithoutSaving() {
        long before = orderRepository.count();
        badRequest(() -> create(item(first, "1"), item(first, "2")));
        badRequest(() -> create());
        for (String quantity : List.of("0", "-1", "0.0001", "1000000000000000")) {
            badRequest(() -> create(item(first, quantity)));
        }
        productService.disable(first.getId(), "manager");
        badRequest(() -> create(item(first, "1")));
        assertThat(orderRepository.count()).isEqualTo(before);
    }

    @Test
    void reservesEveryItemOnceInAscendingProductLockOrderAndDoesNotChangePhysicalStock() {
        var order = create(item(second, "30"), item(first, "20"));
        var checked = orderService.checkInventory(order.id(), "sales");
        assertThat(checked.status()).isEqualTo(OrderStatus.PENDING_OUTBOUND);
        assertThat(checked.exceptionReason()).isNull();
        assertThat(outboundRepository.findByOrderId(order.id())).hasSize(2)
                .allMatch(row -> row.getStatus() == OutboundStatus.PENDING);
        verify(inventoryRepository).findAllByProductIdInForUpdate(List.of(first.getId(), second.getId()));
        assertThat(inventoryService.inventoryForProduct(first.getId()).physicalQuantity()).isEqualByComparingTo("100");
        assertThat(inventoryService.inventoryForProduct(first.getId()).availableQuantity()).isEqualByComparingTo("80");
        conflict(() -> orderService.checkInventory(order.id(), "sales"));
        conflict(() -> orderService.recheckInventory(order.id(), "sales"));
        assertThat(outboundRepository.findByOrderId(order.id())).hasSize(2);
    }

    @Test
    void shortageIncludesPendingReservationsAndCreatesNoPartialOutboundRows() {
        var prior = create(item(first, "30"));
        orderService.checkInventory(prior.id(), "manager");
        var order = create(item(second, "20"), item(first, "100"));
        var checked = orderService.checkInventory(order.id(), "sales");
        assertThat(checked.status()).isEqualTo(OrderStatus.ABNORMAL);
        assertThat(checked.exceptionReason()).isEqualTo(first.getSku() + ": 需要 100.000，可用 70.000");
        assertThat(orderService.getOrder(order.id(), "sales").status()).isEqualTo(OrderStatus.ABNORMAL);
        assertThat(outboundRepository.findByOrderId(order.id())).isEmpty();
        conflict(() -> orderService.checkInventory(order.id(), "sales"));
    }

    @Test
    void recheckUpdatesAllShortagesInProductOrderAndClearsReasonAfterInbound() {
        var order = create(item(second, "100"), item(first, "120"));
        conflict(() -> orderService.recheckInventory(order.id(), "sales"));
        var checked = orderService.checkInventory(order.id(), "sales");
        assertThat(checked.exceptionReason()).isEqualTo(first.getSku() + ": 需要 120.000，可用 100.000; "
                + second.getSku() + ": 需要 100.000，可用 70.000");
        inventoryService.recordInbound(first.getId(), new BigDecimal("20"), null, "warehouse");
        assertThat(orderService.recheckInventory(order.id(), "sales").exceptionReason())
                .isEqualTo(second.getSku() + ": 需要 100.000，可用 70.000");
        assertThat(outboundRepository.findByOrderId(order.id())).isEmpty();
        inventoryService.recordInbound(second.getId(), new BigDecimal("30"), null, "warehouse");
        var rechecked = orderService.recheckInventory(order.id(), "manager");
        assertThat(rechecked.status()).isEqualTo(OrderStatus.PENDING_OUTBOUND);
        assertThat(rechecked.exceptionReason()).isNull();
        assertThat(outboundRepository.findByOrderId(order.id())).hasSize(2);
    }

    @Test
    void cancellationReleasesAllPendingReservationsAndCannotBeRepeatedOrChecked() {
        var order = create(item(first, "30"), item(second, "20"));
        orderService.checkInventory(order.id(), "sales");
        assertThat(orderService.cancelOrder(order.id(), "manager").status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(outboundRepository.findByOrderId(order.id())).hasSize(2)
                .allMatch(row -> row.getStatus() == OutboundStatus.CANCELLED);
        assertThat(inventoryService.inventoryForProduct(first.getId()).availableQuantity()).isEqualByComparingTo("100");
        conflict(() -> orderService.cancelOrder(order.id(), "sales"));
        conflict(() -> orderService.checkInventory(order.id(), "sales"));
        conflict(() -> orderService.recheckInventory(order.id(), "sales"));
    }

    @Test
    void cancelsUncheckedAndStockShortageOrders() {
        var unchecked = create(item(first, "1"));
        assertThat(orderService.cancelOrder(unchecked.id(), "sales").status()).isEqualTo(OrderStatus.CANCELLED);
        var abnormal = create(item(first, "200"));
        orderService.checkInventory(abnormal.id(), "sales");
        assertThat(orderService.cancelOrder(abnormal.id(), "sales").status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void completedOutboundPreventsCancelAndDifferenceOrderCannotGenerateReplacementRows() {
        var order = create(item(first, "20"), item(second, "20"));
        orderService.checkInventory(order.id(), "sales");
        var row = outboundRepository.findByOrderId(order.id()).getFirst();
        jdbc.update("update outbound_record set status = 'COMPLETED', actual_quantity = 10 where id = ?", row.getId());
        jdbc.update("update sales_order set status = 'ABNORMAL' where id = ?", order.id());
        conflict(() -> orderService.cancelOrder(order.id(), "sales"));
        conflict(() -> orderService.recheckInventory(order.id(), "sales"));
        assertThat(outboundRepository.findByOrderId(order.id())).hasSize(2);
    }

    @Test
    void serviceMutationsRejectWarehouseUnknownDisabledAndDowngradedUsers() {
        var order = create(item(first, "200"));
        for (String user : List.of("warehouse", "missing")) {
            forbidden(() -> orderService.createOrder(command(item(first, "1")), user));
            forbidden(() -> orderService.checkInventory(order.id(), user));
            forbidden(() -> orderService.recheckInventory(order.id(), user));
            forbidden(() -> orderService.cancelOrder(order.id(), user));
        }
        jdbc.update("update app_user set enabled = false where username = 'sales'");
        forbidden(() -> orderService.checkInventory(order.id(), "sales"));
        forbidden(() -> orderService.getOrder(order.id(), "sales"));
        jdbc.update("update app_user set role = 'WAREHOUSE' where username = 'manager'");
        forbidden(() -> orderService.cancelOrder(order.id(), "manager"));
        assertThat(orderService.getOrder(order.id(), "warehouse").status()).isEqualTo(OrderStatus.PENDING_CHECK);
        assertThat(outboundRepository.findByOrderId(order.id())).isEmpty();
    }

    @Test
    @WithMockUser(username = "sales", roles = "SALES")
    void apiSupportsCreateDetailCheckFilterRecheckAndCancel() throws Exception {
        String response = mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(command(item(first, "120")))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(response).get("id").asLong();
        mvc.perform(get("/api/orders/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(post("/api/orders/{id}/check-inventory", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABNORMAL"));
        mvc.perform(get("/api/orders").param("status", "ABNORMAL").param("keyword", "Customer").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/orders/abnormal")).andExpect(status().isOk());
        mvc.perform(get("/api/orders")).andExpect(status().isOk());
        mvc.perform(get("/api/orders").param("size", "201")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/orders/{id}/recheck-inventory", id)).andExpect(status().isOk());
        mvc.perform(post("/api/orders/{id}/cancel", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{\"customerName\":\"\",\"items\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "sales", roles = "SALES")
    void staleSalesTokenCannotMutateAfterRoleDowngrade() throws Exception {
        var order = create(item(first, "1"));
        jdbc.update("update app_user set role = 'WAREHOUSE' where username = 'sales'");
        mvc.perform(post("/api/orders/{id}/check-inventory", order.id())).andExpect(status().isForbidden());
        assertThat(orderService.getOrder(order.id(), "warehouse").status()).isEqualTo(OrderStatus.PENDING_CHECK);
    }

    @Test
    void failureWritingSecondOutboundRollsBackFirstRecordAndOrderState() {
        var order = create(item(first, "20"), item(second, "30"));
        doThrow(new IllegalStateException("Simulated second outbound insert failure"))
                .when(outboundRepository).save(argThat(record -> record.getProduct().getId().equals(second.getId())));

        assertThatThrownBy(() -> orderService.checkInventory(order.id(), "sales"))
                .isInstanceOf(IllegalStateException.class).hasMessage("Simulated second outbound insert failure");

        assertThat(outboundRepository.findByOrderId(order.id())).isEmpty();
        assertThat(orderService.getOrder(order.id(), "sales").status()).isEqualTo(OrderStatus.PENDING_CHECK);
        assertThat(inventoryService.inventoryForProduct(first.getId()).availableQuantity()).isEqualByComparingTo("100");
    }

    @Test
    void concurrentChecksOfOneOrderCreateOnlyOneSetOfOutboundRecords() throws Exception {
        var order = create(item(first, "20"), item(second, "30"));
        var start = new CyclicBarrier(2);
        java.util.concurrent.Callable<String> check = () -> {
            start.await(10, TimeUnit.SECONDS);
            try {
                return orderService.checkInventory(order.id(), "sales").status().name();
            } catch (ResponseStatusException ex) {
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                return "CONFLICT";
            }
        };
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(check);
            var b = workers.submit(check);
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("PENDING_OUTBOUND", "CONFLICT");
        }
        assertThat(outboundRepository.findByOrderId(order.id())).hasSize(2);
    }

    @Test
    void concurrentOrdersWaitForInventoryLockAndCannotReserveTheSameStock() throws Exception {
        var a = create(item(first, "60"), item(second, "50"));
        var b = create(item(second, "50"), item(first, "60"));
        CountDownLatch reserved = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var firstCheck = workers.submit(() -> new TransactionTemplate(transactionManager).execute(tx -> {
                var result = orderService.checkInventory(a.id(), "sales");
                reserved.countDown();
                await(commit);
                return result;
            }));
            try {
                assertThat(reserved.await(15, TimeUnit.SECONDS)).isTrue();
                var secondCheck = workers.submit(() -> orderService.checkInventory(b.id(), "sales"));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                boolean blockedOnInventory = false;
                while (System.nanoTime() < deadline && !blockedOnInventory) {
                    blockedOnInventory = jdbc.queryForObject("select count(*) from pg_stat_activity "
                            + "where datname = current_database() and wait_event_type = 'Lock' "
                            + "and query ilike '%inventory%' and pid <> pg_backend_pid()", Integer.class) > 0;
                    if (!blockedOnInventory) Thread.sleep(25);
                }
                assertThat(blockedOnInventory).as("second PostgreSQL transaction waits on the inventory lock").isTrue();
                assertThat(secondCheck.isDone()).isFalse();
                commit.countDown();
                assertThat(firstCheck.get(15, TimeUnit.SECONDS).status()).isEqualTo(OrderStatus.PENDING_OUTBOUND);
                assertThat(secondCheck.get(15, TimeUnit.SECONDS).status()).isEqualTo(OrderStatus.ABNORMAL);
                assertThat(outboundRepository.findByOrderId(a.id())).hasSize(2);
                assertThat(outboundRepository.findByOrderId(b.id())).isEmpty();
                assertThat(inventoryService.inventoryForProduct(first.getId()).availableQuantity()).isEqualByComparingTo("40");
                assertThat(inventoryService.inventoryForProduct(second.getId()).availableQuantity()).isEqualByComparingTo("20");
            } finally {
                commit.countDown();
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for test transaction");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private OrderDtos.ItemRequest item(Product product, String quantity) {
        return new OrderDtos.ItemRequest(product.getId(), new BigDecimal(quantity));
    }

    private OrderDtos.CreateOrderRequest command(OrderDtos.ItemRequest... items) {
        return new OrderDtos.CreateOrderRequest("Customer", List.of(items));
    }

    private OrderDtos.OrderResponse create(OrderDtos.ItemRequest... items) {
        return orderService.createOrder(command(items), "sales");
    }

    private void badRequest(Runnable action) { rejects(action, HttpStatus.BAD_REQUEST); }
    private void conflict(Runnable action) { rejects(action, HttpStatus.CONFLICT); }
    private void forbidden(Runnable action) { rejects(action, HttpStatus.FORBIDDEN); }
    private void rejects(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(status));
    }
}
