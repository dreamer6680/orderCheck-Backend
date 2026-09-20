package com.packflow.app.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packflow.app.inventory.InventoryService;
import com.packflow.app.dashboard.WarehouseProgressService;
import com.packflow.app.order.OrderDtos;
import com.packflow.app.order.OrderService;
import com.packflow.app.order.OrderStatus;
import com.packflow.app.order.SalesOrderRepository;
import com.packflow.app.product.Product;
import com.packflow.app.product.ProductRepository;
import com.packflow.app.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OutboundServiceTest extends PostgresIntegrationTest {

    @Autowired private OutboundService outboundService;
    @Autowired private WarehouseProgressService warehouseProgress;
    @Autowired private OutboundRecordRepository outboundRepository;
    @Autowired private OrderService orderService;
    @Autowired private SalesOrderRepository orderRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryService inventoryService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    private Product first;
    private Product second;

    @BeforeEach
    void stockProducts() {
        first = productRepository.save(new Product(
                "OUT-A-" + System.nanoTime(), "Outbound A", "件", BigDecimal.ZERO, true));
        second = productRepository.save(new Product(
                "OUT-B-" + System.nanoTime(), "Outbound B", "箱", BigDecimal.ZERO, true));
        inventoryService.recordInbound(first.getId(), new BigDecimal("100.000"), null, "warehouse");
        inventoryService.recordInbound(second.getId(), new BigDecimal("80.000"), null, "warehouse");
    }

    @AfterEach
    void restoreUsers() {
        jdbc.update("update app_user set role = 'WAREHOUSE', enabled = true where username = 'warehouse'");
        jdbc.update("update app_user set role = 'MANAGER', enabled = true where username = 'manager'");
    }

    @Test
    void exactCompletionsDeductPhysicalStockAndCompleteOrderAfterEveryLine() {
        var order = reserve(item(first, "20.000"), item(second, "30.000"));
        var rows = rows(order.id());

        var firstResult = outboundService.complete(rows.get(0).getId(), rows.get(0).getPlannedQuantity(), null, "warehouse");

        assertThat(firstResult.status()).isEqualTo(OutboundStatus.COMPLETED);
        assertThat(firstResult.operatorUsername()).isEqualTo("warehouse");
        assertThat(firstResult.completedAt()).isNotNull();
        assertThat(orderRepository.findById(order.id()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING_OUTBOUND);

        outboundService.complete(rows.get(1).getId(), rows.get(1).getPlannedQuantity(), null, "manager");

        assertThat(orderRepository.findById(order.id()).orElseThrow().getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(inventoryService.inventoryForProduct(first.getId()).physicalQuantity()).isEqualByComparingTo("80.000");
        assertThat(inventoryService.inventoryForProduct(second.getId()).physicalQuantity()).isEqualByComparingTo("50.000");
    }

    @Test
    void futureDeliveryDoesNotPreventEarlyOutboundAndRemovesOrderFromPendingProgress() {
        var order = reserve(item(first, "10.000"));
        var record = rows(order.id()).getFirst();
        var before = warehouseProgress.today();

        assertThat(order.deliveryDate()).isAfter(before.businessDate());
        assertThat(record.getPlannedOutboundDate()).isEqualTo(order.deliveryDate());
        assertThat(outboundService.checkInventory(record.getId(), "warehouse").executable()).isTrue();

        outboundService.complete(record.getId(), new BigDecimal("10.000"), null, "warehouse");

        assertThat(orderRepository.findById(order.id()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.COMPLETED);
        assertThat(warehouseProgress.today().pendingOutboundOrderCount())
                .isEqualTo(before.pendingOutboundOrderCount() - 1);
    }

    @Test
    void shortageThenRestockAndSupplementalShipmentCompletesOrderWithoutDuplicateStockDeduction() {
        var order = reserve(item(first, "100.000"));
        var firstTask = rows(order.id()).getFirst();
        outboundService.complete(firstTask.getId(), new BigDecimal("80.000"), "20 damaged", "warehouse");

        var afterFirst = orderService.getOrder(order.id(), "sales");
        assertThat(afterFirst.status()).isEqualTo(OrderStatus.ABNORMAL);
        assertThat(afterFirst.items().getFirst().shippedQuantity()).isEqualByComparingTo("80");
        assertThat(afterFirst.items().getFirst().remainingQuantity()).isEqualByComparingTo("20");
        assertThat(afterFirst.events()).anyMatch(event ->
                event.eventType() == com.packflow.app.order.OrderEventType.OUTBOUND_SHORTAGE);
        conflict(() -> orderService.planSupplemental(order.id(),
                afterFirst.items().getFirst().id(), new BigDecimal("21"), "sales"));

        // Another order may reserve the remaining physical stock. Supplemental must wait for restock.
        var other = reserve(item(first, "20.000"));
        conflict(() -> orderService.planSupplemental(order.id(),
                afterFirst.items().getFirst().id(), null, "sales"));
        inventoryService.recordInbound(first.getId(), new BigDecimal("20"), null, "warehouse");

        var planned = orderService.planSupplemental(order.id(),
                afterFirst.items().getFirst().id(), null, "sales");
        assertThat(planned.items().getFirst().pendingQuantity()).isEqualByComparingTo("20");
        assertThat(planned.items().getFirst().shippedQuantity()).isEqualByComparingTo("80");
        conflict(() -> orderService.planSupplemental(order.id(),
                afterFirst.items().getFirst().id(), null, "sales"));
        var tasks = rows(order.id());
        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(1).getShipmentType()).isEqualTo(ShipmentType.SUPPLEMENTAL);

        outboundService.complete(tasks.get(1).getId(), new BigDecimal("20"), null, "warehouse");

        var completed = orderService.getOrder(order.id(), "sales");
        assertThat(completed.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(completed.items().getFirst().shippedQuantity()).isEqualByComparingTo("100");
        assertThat(completed.items().getFirst().remainingQuantity()).isEqualByComparingTo("0");
        assertThat(completed.events()).anyMatch(event ->
                event.eventType() == com.packflow.app.order.OrderEventType.OUTBOUND_SHORTAGE);
        assertThat(inventoryService.inventoryForProduct(first.getId()).physicalQuantity())
                .isEqualByComparingTo("20");
    }

    @Test
    void customerMayAcceptRemainingShortQuantityWithoutForgingAnotherShipment() {
        var order = reserve(item(first, "10.000"));
        var task = rows(order.id()).getFirst();
        outboundService.complete(task.getId(), new BigDecimal("8"), "Customer accepts 8", "warehouse");

        var accepted = orderService.acceptShortDelivery(order.id(), "Customer approves 2 fewer", "sales");
        assertThat(accepted.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(accepted.items().getFirst().shippedQuantity()).isEqualByComparingTo("8");
        assertThat(accepted.items().getFirst().waivedQuantity()).isEqualByComparingTo("2");
        assertThat(accepted.items().getFirst().remainingQuantity()).isEqualByComparingTo("0");
        assertThat(rows(order.id())).hasSize(1);
        assertThat(accepted.events()).anyMatch(event ->
                event.eventType() == com.packflow.app.order.OrderEventType.CUSTOMER_ACCEPTED_SHORTAGE);
    }

    @Test
    void agreedPartialFirstShipmentKeepsTheRestOutstandingUntilSupplementalDelivery() {
        var order = orderService.createOrder(new OrderDtos.CreateOrderRequest(
                "Part shipment", LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")),
                List.of(item(first, "120"))), "sales");
        var checked = orderService.checkInventory(order.id(), "sales");
        assertThat(checked.abnormalType()).isEqualTo(com.packflow.app.order.OrderAbnormalType.STOCK_SHORTAGE);
        var scheduled = orderService.planPartialOutbound(order.id(), true, "sales");
        assertThat(scheduled.status()).isEqualTo(OrderStatus.PENDING_OUTBOUND);
        assertThat(scheduled.items().getFirst().pendingQuantity()).isEqualByComparingTo("100");
        conflict(() -> orderService.planPartialOutbound(order.id(), true, "sales"));

        var firstTask = rows(order.id()).getFirst();
        outboundService.complete(firstTask.getId(), new BigDecimal("100"), null, "warehouse");
        var partial = orderService.getOrder(order.id(), "sales");
        assertThat(partial.abnormalType()).isEqualTo(com.packflow.app.order.OrderAbnormalType.SHORT_DELIVERY);
        assertThat(partial.items().getFirst().remainingQuantity()).isEqualByComparingTo("20");

        inventoryService.recordInbound(first.getId(), new BigDecimal("20"), null, "warehouse");
        orderService.planSupplemental(order.id(), partial.items().getFirst().id(), null, "sales");
        outboundService.complete(rows(order.id()).get(1).getId(), new BigDecimal("20"), null, "warehouse");
        assertThat(orderService.getOrder(order.id(), "sales").status()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void quantityDifferenceRequiresReasonAndMarksOrderAbnormal() {
        var order = reserve(item(first, "10.000"));
        var row = rows(order.id()).getFirst();

        badRequest(() -> outboundService.complete(row.getId(), new BigDecimal("8.000"), " ", "warehouse"));
        assertThat(inventoryService.inventoryForProduct(first.getId()).physicalQuantity()).isEqualByComparingTo("100.000");
        assertThat(outboundRepository.findById(row.getId()).orElseThrow().getStatus()).isEqualTo(OutboundStatus.PENDING);

        var completed = outboundService.complete(row.getId(), new BigDecimal("8.000"), "2 damaged", "warehouse");

        assertThat(completed.actualQuantity()).isEqualByComparingTo("8.000");
        assertThat(completed.differenceReason()).isEqualTo("2 damaged");
        assertThat(inventoryService.inventoryForProduct(first.getId()).physicalQuantity()).isEqualByComparingTo("92.000");
        var stored = orderRepository.findById(order.id()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(OrderStatus.ABNORMAL);
        assertThat(stored.getAbnormalType()).isEqualTo(com.packflow.app.order.OrderAbnormalType.SHORT_DELIVERY);
        assertThat(stored.getExceptionReason()).contains(first.getSku(), "10.000", "8.000", "2 damaged");
    }

    @Test
    void shippedShortDeliveryCannotBeRelabeledAsUnableToDeliver() {
        var order = reserve(item(first, "10.000"));
        var row = rows(order.id()).getFirst();
        outboundService.complete(row.getId(), new BigDecimal("8.000"), "damaged", "warehouse");

        conflict(() -> orderService.markUnableToDeliver(order.id(), "Cannot deliver", "sales"));
        assertThat(orderRepository.findById(order.id()).orElseThrow().getAbnormalType())
                .isEqualTo(com.packflow.app.order.OrderAbnormalType.SHORT_DELIVERY);
    }

    @Test
    void laterExactCompletionDoesNotOverwriteEarlierDifferenceDetails() {
        var order = reserve(item(first, "10.000"), item(second, "12.000"));
        var rows = rows(order.id());
        var firstRow = rows.stream().filter(row -> row.getProduct().getId().equals(first.getId())).findFirst().orElseThrow();
        var secondRow = rows.stream().filter(row -> row.getProduct().getId().equals(second.getId())).findFirst().orElseThrow();

        outboundService.complete(firstRow.getId(), new BigDecimal("8.000"), "2 damaged", "warehouse");
        outboundService.complete(secondRow.getId(), new BigDecimal("12.000"), null, "warehouse");

        var stored = orderRepository.findById(order.id()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(OrderStatus.ABNORMAL);
        assertThat(stored.getExceptionReason()).contains(first.getSku(), "10.000", "8.000", "2 damaged");
    }

    @Test
    void invalidActualQuantitiesDoNotMutateStockOrReservation() {
        var order = reserve(item(first, "10.000"));
        var row = rows(order.id()).getFirst();

        for (String quantity : List.of("0", "-1", "10.001", "0.0001", "1000000000000000")) {
            badRequest(() -> outboundService.complete(row.getId(), new BigDecimal(quantity), null, "warehouse"));
        }

        assertThat(inventoryService.inventoryForProduct(first.getId()).physicalQuantity()).isEqualByComparingTo("100.000");
        assertThat(inventoryService.inventoryForProduct(first.getId()).pendingQuantity()).isEqualByComparingTo("10.000");
        assertThat(outboundRepository.findById(row.getId()).orElseThrow().getStatus()).isEqualTo(OutboundStatus.PENDING);
    }

    @Test
    void insufficientPhysicalStockRollsBackOutboundCompletion() {
        var order = reserve(item(first, "10.000"));
        var row = rows(order.id()).getFirst();
        jdbc.update("update inventory set quantity = 5.000 where product_id = ?", first.getId());

        conflict(() -> outboundService.complete(row.getId(), new BigDecimal("10.000"), null, "warehouse"));

        assertThat(outboundRepository.findById(row.getId()).orElseThrow().getStatus()).isEqualTo(OutboundStatus.PENDING);
        assertThat(inventoryService.inventoryForProduct(first.getId()).physicalQuantity()).isEqualByComparingTo("5.000");
        assertThat(orderRepository.findById(order.id()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING_OUTBOUND);
    }

    @Test
    void completionCannotConsumeStockReservedForAnotherPendingTask() {
        var firstOrder = reserve(item(first, "6.000"));
        var secondOrder = reserve(item(first, "4.000"));
        var firstTask = rows(firstOrder.id()).getFirst();
        var secondTask = rows(secondOrder.id()).getFirst();
        jdbc.update("update inventory set quantity = 7.000 where product_id = ?", first.getId());

        var check = outboundService.checkInventory(firstTask.getId(), "warehouse");
        assertThat(check.executable()).isFalse();
        assertThat(check.availableForTask()).isEqualByComparingTo("3.000");

        conflict(() -> outboundService.complete(firstTask.getId(), new BigDecimal("6.000"), null, "warehouse"));
        assertThat(outboundRepository.findById(firstTask.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboundStatus.PENDING);
        assertThat(outboundRepository.findById(secondTask.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboundStatus.PENDING);
        assertThat(inventoryService.inventoryForProduct(first.getId()).physicalQuantity())
                .isEqualByComparingTo("7.000");
    }

    @Test
    void completedRecordCannotBeCompletedOrCancelledAgain() {
        var order = reserve(item(first, "10.000"));
        var row = rows(order.id()).getFirst();
        outboundService.complete(row.getId(), new BigDecimal("10.000"), null, "warehouse");

        conflict(() -> outboundService.complete(row.getId(), new BigDecimal("10.000"), null, "warehouse"));
        conflict(() -> outboundService.cancel(row.getId(), "manager"));
        assertThat(inventoryService.inventoryForProduct(first.getId()).physicalQuantity()).isEqualByComparingTo("90.000");
    }

    @Test
    void cancellingPendingRecordReleasesReservationAndMarksOrderAbnormal() {
        var order = reserve(item(first, "10.000"));
        var row = rows(order.id()).getFirst();
        assertThat(inventoryService.inventoryForProduct(first.getId()).availableQuantity()).isEqualByComparingTo("90.000");

        var cancelled = outboundService.cancel(row.getId(), "manager");

        assertThat(cancelled.status()).isEqualTo(OutboundStatus.CANCELLED);
        assertThat(inventoryService.inventoryForProduct(first.getId()).availableQuantity()).isEqualByComparingTo("100.000");
        assertThat(orderRepository.findById(order.id()).orElseThrow().getStatus()).isEqualTo(OrderStatus.ABNORMAL);
    }

    @Test
    void serviceUsesCurrentPersistedWarehouseOrManagerRole() {
        var order = reserve(item(first, "10.000"));
        var row = rows(order.id()).getFirst();
        forbidden(() -> outboundService.complete(row.getId(), new BigDecimal("10.000"), null, "sales"));
        forbidden(() -> outboundService.cancel(row.getId(), "sales"));

        jdbc.update("update app_user set enabled = false where username = 'warehouse'");
        forbidden(() -> outboundService.complete(row.getId(), new BigDecimal("10.000"), null, "warehouse"));
        jdbc.update("update app_user set role = 'SALES' where username = 'manager'");
        forbidden(() -> outboundService.cancel(row.getId(), "manager"));
        assertThat(outboundRepository.findById(row.getId()).orElseThrow().getStatus()).isEqualTo(OutboundStatus.PENDING);
    }

    @Test
    @WithMockUser(username = "warehouse", roles = "WAREHOUSE")
    void apiListsAndCompletesPendingOutboundRecords() throws Exception {
        var order = reserve(item(first, "10.000"));
        var row = rows(order.id()).getFirst();

        mvc.perform(get("/api/outbound-records").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(row.getId()))
                .andExpect(jsonPath("$[0].orderId").value(order.id()));
        mvc.perform(post("/api/outbound-records/{id}/complete", row.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new OutboundDtos.CompleteRequest(new BigDecimal("10.000"), null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @WithMockUser(username = "warehouse", roles = "WAREHOUSE")
    void reschedulingPendingTaskUpdatesTodaysShareAndIsRejectedAfterCompletion() throws Exception {
        var order = reserve(item(first, "10.000"));
        var task = rows(order.id()).getFirst();
        var before = warehouseProgress.today();
        LocalDate originalDeliveryDate = order.deliveryDate();
        assertThat(task.getPlannedOutboundDate()).isEqualTo(originalDeliveryDate);
        assertThat(originalDeliveryDate).isAfter(before.businessDate());

        mvc.perform(patch("/api/outbound-records/{id}/schedule", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedOutboundDate\":\"" + before.businessDate() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedOutboundDate").value(before.businessDate().toString()));
        var after = warehouseProgress.today();
        assertThat(after.pendingOutboundCount()).isEqualTo(before.pendingOutboundCount());
        assertThat(after.todayPendingOutboundCount()).isEqualTo(before.todayPendingOutboundCount() + 1);
        assertThat(after.todayDuePendingOrderCount()).isEqualTo(before.todayDuePendingOrderCount());
        assertThat(outboundRepository.findById(task.getId()).orElseThrow().getPlannedOutboundDate())
                .isEqualTo(before.businessDate());

        outboundService.complete(task.getId(), new BigDecimal("10.000"), null, "warehouse");
        mvc.perform(patch("/api/outbound-records/{id}/schedule", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedOutboundDate\":\"" + before.businessDate() + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "sales", roles = "SALES")
    void apiRejectsSalesRoleForOutboundOperations() throws Exception {
        var order = reserve(item(first, "10.000"));
        var row = rows(order.id()).getFirst();

        mvc.perform(get("/api/outbound-records")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/outbound-records/{id}/schedule", row.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"plannedOutboundDate\":\"2026-09-19\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/outbound-records/{id}/complete", row.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualQuantity\":10}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/outbound-records/{id}/cancel", row.getId())).andExpect(status().isForbidden());
    }


    @Test
    void pendingTaskCheckReportsStockAndNeverMutatesInventory() {
        var order = reserve(item(first, "20.000"));
        var row = rows(order.id()).getFirst();

        var check = outboundService.checkInventory(row.getId(), "warehouse");

        assertThat(check.executable()).isTrue();
        assertThat(check.reason()).isNull();
        assertThat(check.plannedQuantity()).isEqualByComparingTo("20.000");
        assertThat(check.physicalQuantity()).isEqualByComparingTo("100.000");
        assertThat(check.reservedQuantity()).isEqualByComparingTo("20.000");
        assertThat(check.availableForTask()).isEqualByComparingTo("100.000");
        assertThat(check.checkedAt()).isNotNull();
        assertThat(outboundRepository.findById(row.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboundStatus.PENDING);
        assertThat(inventoryService.inventoryForProduct(first.getId()).physicalQuantity())
                .isEqualByComparingTo("100.000");
    }

    @Test
    void pendingTaskCheckDetectsShortageWithoutChangingTaskStatus() {
        var order = reserve(item(first, "20.000"));
        var row = rows(order.id()).getFirst();
        jdbc.update("update inventory set quantity = 5.000 where product_id = ?", first.getId());

        var check = outboundService.checkInventory(row.getId(), "warehouse");

        assertThat(check.executable()).isFalse();
        assertThat(check.reason()).isEqualTo("Insufficient physical inventory");
        assertThat(check.physicalQuantity()).isEqualByComparingTo("5.000");
        assertThat(outboundRepository.findById(row.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboundStatus.PENDING);
    }

    @Test
    void completedTaskIsNotEligibleForAnotherCheck() {
        var order = reserve(item(first, "10.000"));
        var row = rows(order.id()).getFirst();
        outboundService.complete(row.getId(), new BigDecimal("10.000"), null, "warehouse");

        var check = outboundService.checkInventory(row.getId(), "warehouse");

        assertThat(check.executable()).isFalse();
        assertThat(check.reason()).isEqualTo("Outbound record is no longer pending");
    }

    @Test
    @WithMockUser(username = "warehouse", roles = "WAREHOUSE")
    void apiExposesTaskDetailAndInventoryCheck() throws Exception {
        var order = reserve(item(first, "10.000"));
        var row = rows(order.id()).getFirst();

        mvc.perform(get("/api/outbound-records/{id}", row.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordNo").value(row.getRecordNo()));
        mvc.perform(get("/api/outbound-records/{id}/check-inventory", row.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executable").value(true))
                .andExpect(jsonPath("$.plannedQuantity").value(10.000))
                .andExpect(jsonPath("$.availableForTask").value(100.000));
        mvc.perform(get("/api/outbound-records/{id}/check-inventory", -999))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "sales", roles = "SALES")
    void apiRejectsSalesRoleForTaskInventoryCheck() throws Exception {
        var order = reserve(item(first, "10.000"));
        var row = rows(order.id()).getFirst();
        mvc.perform(get("/api/outbound-records/{id}/check-inventory", row.getId()))
                .andExpect(status().isForbidden());
    }

    private OrderDtos.OrderResponse reserve(OrderDtos.ItemRequest... items) {
        var order = orderService.createOrder(new OrderDtos.CreateOrderRequest("Outbound customer", LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(2), List.of(items)), "sales");
        return orderService.checkInventory(order.id(), "sales");
    }

    private List<OutboundRecord> rows(Long orderId) {
        return outboundRepository.findByOrderId(orderId).stream()
                .sorted(Comparator.comparing(record -> record.getProduct().getId()))
                .toList();
    }

    private OrderDtos.ItemRequest item(Product product, String quantity) {
        return new OrderDtos.ItemRequest(product.getId(), new BigDecimal(quantity));
    }

    private void badRequest(Runnable action) {
        rejects(action, HttpStatus.BAD_REQUEST);
    }

    private void conflict(Runnable action) {
        rejects(action, HttpStatus.CONFLICT);
    }

    private void forbidden(Runnable action) {
        rejects(action, HttpStatus.FORBIDDEN);
    }

    private void rejects(Runnable action, HttpStatus expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(expected));
    }
}
