package com.packflow.app.order;

import com.packflow.app.inventory.Inventory;
import com.packflow.app.inventory.InventoryRepository;
import com.packflow.app.order.OrderDtos.CreateOrderRequest;
import com.packflow.app.order.OrderDtos.ItemResponse;
import com.packflow.app.order.OrderDtos.OrderResponse;
import com.packflow.app.outbound.OutboundRecord;
import com.packflow.app.outbound.OutboundRecordRepository;
import com.packflow.app.outbound.OutboundStatus;
import com.packflow.app.product.Product;
import com.packflow.app.product.ProductRepository;
import com.packflow.app.user.AppUser;
import com.packflow.app.user.AppUserRepository;
import com.packflow.app.user.Role;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {
    private final SalesOrderRepository orders;
    private final SalesOrderItemRepository items;
    private final ProductRepository products;
    private final InventoryRepository inventories;
    private final OutboundRecordRepository outbounds;
    private final AppUserRepository users;
    private final Validator validator;
    private final ZoneId warehouseZone;

    public OrderService(SalesOrderRepository orders, SalesOrderItemRepository items, ProductRepository products,
            InventoryRepository inventories, OutboundRecordRepository outbounds, AppUserRepository users,
            Validator validator, @Value("${app.warehouse.time-zone}") String warehouseTimeZone) {
        this.orders = orders;
        this.items = items;
        this.products = products;
        this.inventories = inventories;
        this.outbounds = outbounds;
        this.users = users;
        this.validator = validator;
        this.warehouseZone = ZoneId.of(warehouseTimeZone);
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, String username) {
        AppUser creator = requireUser(username, true);
        if (request == null || !validator.validate(request).isEmpty()) {
            throw error(HttpStatus.BAD_REQUEST, "Customer and at least one valid order item are required");
        }
        var productIds = new HashSet<Long>();
        var orderedProducts = new ArrayList<Product>();
        for (var item : request.items()) {
            if (!productIds.add(item.productId())) {
                throw error(HttpStatus.BAD_REQUEST, "Duplicate product in order");
            }
            Product product = products.findById(item.productId())
                    .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Product not found"));
            if (!product.isEnabled()) throw error(HttpStatus.BAD_REQUEST, "Product is disabled");
            orderedProducts.add(product);
        }
        SalesOrder order = orders.save(new SalesOrder(nextNumber("SO"), request.customerName().trim(), creator, request.deliveryDate()));
        for (int i = 0; i < request.items().size(); i++) {
            items.save(new SalesOrderItem(order, orderedProducts.get(i), request.items().get(i).orderedQuantity()));
        }
        return response(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listOrders(OrderStatus status, String keyword, int page, int size, String username) {
        requireUser(username, false);
        if (page < 0 || size < 1 || size > 200) throw error(HttpStatus.BAD_REQUEST, "Invalid page or size (1-200)");
        String pattern = "%" + (keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT)) + "%";
        return orders.search(status, pattern, PageRequest.of(page, size)).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId, String username) {
        requireUser(username, false);
        return response(orders.findById(orderId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Order not found")));
    }

    @Transactional
    public OrderResponse changeDeliveryDate(Long orderId, LocalDate deliveryDate, String username) {
        requireUser(username, true);
        if (deliveryDate == null) throw error(HttpStatus.BAD_REQUEST, "Delivery date is required");
        SalesOrder order = lockOrder(orderId);
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw error(HttpStatus.CONFLICT, "Cannot change delivery date of completed or cancelled order");
        }
        order.changeDeliveryDate(deliveryDate);
        // The outbound plan is separate: moving a customer's delivery promise must not delay an existing task.
        return response(order);
    }

    @Transactional
    public OrderResponse checkInventory(Long orderId, String username) {
        requireUser(username, true);
        SalesOrder order = lockOrder(orderId);
        requireStatus(order, OrderStatus.PENDING_CHECK);
        return reserve(order);
    }

    @Transactional
    public OrderResponse recheckInventory(Long orderId, String username) {
        requireUser(username, true);
        SalesOrder order = lockOrder(orderId);
        requireStatus(order, OrderStatus.ABNORMAL);
        return reserve(order);
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId, String username) {
        requireUser(username, true);
        SalesOrder order = lockOrder(orderId);
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw error(HttpStatus.CONFLICT, "Order cannot be cancelled in its current state");
        }
        lockInventories(items.findByOrderIdOrderByProductIdAsc(orderId));
        List<OutboundRecord> records = outbounds.findByOrderId(orderId);
        if (records.stream().anyMatch(record -> record.getStatus() == OutboundStatus.COMPLETED)) {
            throw error(HttpStatus.CONFLICT, "Order has completed outbound records");
        }
        records.forEach(OutboundRecord::cancelPending);
        order.cancel();
        return response(order);
    }

    private OrderResponse reserve(SalesOrder order) {
        // A difference after execution is not a stock shortage and must never generate replacement tasks.
        if (outbounds.existsByOrderId(order.getId())) {
            throw error(HttpStatus.CONFLICT, "Order already has outbound records");
        }
        List<SalesOrderItem> orderedItems = items.findByOrderIdOrderByProductIdAsc(order.getId());
        Map<Long, Inventory> stock = lockInventories(orderedItems);
        var shortages = new ArrayList<String>();
        for (SalesOrderItem item : orderedItems) {
            Long productId = item.getProduct().getId();
            BigDecimal pending = inventories.pendingOutboundQuantity(productId);
            BigDecimal available = stock.get(productId).getQuantity().subtract(pending == null ? BigDecimal.ZERO : pending);
            if (available.compareTo(item.getOrderedQuantity()) < 0) {
                shortages.add(item.getProduct().getSku() + ": 需要 " + quantity(item.getOrderedQuantity())
                        + "，可用 " + quantity(available));
            }
        }
        if (!shortages.isEmpty()) {
            order.markAbnormal(String.join("; ", shortages));
        } else {
            LocalDate plannedDate = order.getDeliveryDate() == null
                    ? LocalDate.now(warehouseZone) : order.getDeliveryDate();
            for (SalesOrderItem item : orderedItems) {
                outbounds.save(new OutboundRecord(nextNumber("OUT"), item, plannedDate));
            }
            order.markPendingOutbound();
        }
        return response(order);
    }

    private Map<Long, Inventory> lockInventories(List<SalesOrderItem> orderedItems) {
        List<Long> productIds = orderedItems.stream().map(item -> item.getProduct().getId()).sorted().toList();
        if (productIds.isEmpty()) throw error(HttpStatus.CONFLICT, "Order has no items");
        List<Inventory> locked = inventories.findAllByProductIdInForUpdate(productIds);
        if (locked.size() != productIds.size()) throw error(HttpStatus.CONFLICT, "Inventory not found for every order item");
        return locked.stream().collect(Collectors.toMap(inventory -> inventory.getProduct().getId(), Function.identity()));
    }

    private AppUser requireUser(String username, boolean mutation) {
        AppUser user = users.findByUsername(username).filter(AppUser::isEnabled)
                .orElseThrow(() -> error(HttpStatus.FORBIDDEN, "An active user is required"));
        if (mutation && user.getRole() != Role.SALES && user.getRole() != Role.MANAGER) {
            throw error(HttpStatus.FORBIDDEN, "Order changes require an active salesperson or manager");
        }
        return user;
    }

    private SalesOrder lockOrder(Long orderId) {
        return orders.findByIdForUpdate(orderId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private void requireStatus(SalesOrder order, OrderStatus expected) {
        if (order.getStatus() != expected) throw error(HttpStatus.CONFLICT, "Order state does not allow this operation");
    }

    private OrderResponse response(SalesOrder order) {
        List<ItemResponse> details = items.findByOrderIdOrderByProductIdAsc(order.getId()).stream()
                .map(item -> new ItemResponse(item.getId(), item.getProduct().getId(), item.getProduct().getSku(),
                        item.getProduct().getName(), item.getProduct().getUnit(), item.getOrderedQuantity())).toList();
        return new OrderResponse(order.getId(), order.getOrderNo(), order.getCustomerName(), order.getStatus(),
                order.getDeliveryDate(), order.getExceptionReason(), order.getCreatedBy().getUsername(), order.getCreatedAt(), order.getUpdatedAt(), details);
    }

    private String nextNumber(String prefix) { return prefix + "-" + UUID.randomUUID().toString().replace("-", ""); }
    private String quantity(BigDecimal value) { return value.setScale(3).toPlainString(); }
    private ResponseStatusException error(HttpStatus status, String reason) { return new ResponseStatusException(status, reason); }
}
