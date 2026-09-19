package com.packflow.app.outbound;

import com.packflow.app.inventory.Inventory;
import com.packflow.app.inventory.InventoryRepository;
import com.packflow.app.order.OrderStatus;
import com.packflow.app.order.SalesOrder;
import com.packflow.app.order.SalesOrderRepository;
import com.packflow.app.outbound.OutboundDtos.OutboundResponse;
import com.packflow.app.user.AppUser;
import com.packflow.app.user.AppUserRepository;
import com.packflow.app.user.Role;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OutboundService {
    private final OutboundRecordRepository outbounds;
    private final SalesOrderRepository orders;
    private final InventoryRepository inventories;
    private final AppUserRepository users;

    public OutboundService(OutboundRecordRepository outbounds, SalesOrderRepository orders,
            InventoryRepository inventories, AppUserRepository users) {
        this.outbounds = outbounds;
        this.orders = orders;
        this.inventories = inventories;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<OutboundResponse> list(OutboundStatus status, String username) {
        requireOperator(username);
        List<OutboundRecord> records = status == null
                ? outbounds.findAllByOrderByCreatedAtDescIdDesc()
                : outbounds.findByStatusOrderByCreatedAtDescIdDesc(status);
        return records.stream().map(this::response).toList();
    }

    @Transactional
    public OutboundResponse complete(Long recordId, BigDecimal actualQuantity, String differenceReason,
            String username) {
        AppUser operator = requireOperator(username);
        validateQuantity(actualQuantity);

        OutboundRecord snapshot = outbounds.findById(recordId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Outbound record not found"));
        SalesOrder order = orders.findByIdForUpdate(snapshot.getOrder().getId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Order not found"));
        Inventory inventory = inventories.findByProductIdForUpdate(snapshot.getProduct().getId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Inventory not found"));
        OutboundRecord record = lockPending(recordId);

        if (!record.getOrder().getId().equals(order.getId())
                || !record.getProduct().getId().equals(inventory.getProduct().getId())) {
            throw error(HttpStatus.CONFLICT, "Outbound record changed while being processed");
        }
        if (actualQuantity.compareTo(record.getPlannedQuantity()) > 0) {
            throw error(HttpStatus.BAD_REQUEST, "Actual quantity cannot exceed planned quantity");
        }

        boolean differs = actualQuantity.compareTo(record.getPlannedQuantity()) != 0;
        String reason = normalize(differenceReason);
        if (differs && reason == null) {
            throw error(HttpStatus.BAD_REQUEST, "A difference reason is required");
        }

        record.complete(actualQuantity, differs ? reason : null, operator);
        try {
            inventory.recordCompletedOutbound(record);
        } catch (IllegalStateException exception) {
            throw error(HttpStatus.CONFLICT, exception.getMessage());
        }

        if (differs) {
            order.markAbnormal("SKU " + record.getProduct().getSku() + ": planned "
                    + quantity(record.getPlannedQuantity()) + ", actual " + quantity(actualQuantity)
                    + ", reason: " + reason);
        } else {
            finishOrderWhenAllLinesComplete(order);
        }
        return response(record);
    }

    @Transactional
    public OutboundResponse cancel(Long recordId, String username) {
        requireOperator(username);
        OutboundRecord snapshot = outbounds.findById(recordId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Outbound record not found"));
        SalesOrder order = orders.findByIdForUpdate(snapshot.getOrder().getId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Order not found"));
        inventories.findByProductIdForUpdate(snapshot.getProduct().getId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Inventory not found"));
        OutboundRecord record = lockPending(recordId);
        record.cancelPending();
        order.markAbnormal("Outbound record " + record.getRecordNo() + " was cancelled");
        return response(record);
    }

    private void finishOrderWhenAllLinesComplete(SalesOrder order) {
        List<OutboundRecord> records = outbounds.findByOrderId(order.getId());
        if (records.stream().allMatch(record -> record.getStatus() == OutboundStatus.COMPLETED)) {
            if (records.stream().anyMatch(record -> record.getActualQuantity()
                    .compareTo(record.getPlannedQuantity()) != 0)) {
                if (order.getStatus() != OrderStatus.ABNORMAL) {
                    order.markAbnormal("One or more outbound quantities differ from the order");
                }
            } else {
                order.markCompleted();
            }
        }
    }

    private OutboundRecord lockPending(Long recordId) {
        OutboundRecord record = outbounds.findByIdForUpdate(recordId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Outbound record not found"));
        if (record.getStatus() != OutboundStatus.PENDING) {
            throw error(HttpStatus.CONFLICT, "Outbound record is no longer pending");
        }
        return record;
    }

    private AppUser requireOperator(String username) {
        AppUser user = users.findByUsername(username).filter(AppUser::isEnabled)
                .orElseThrow(() -> error(HttpStatus.FORBIDDEN, "An active warehouse operator is required"));
        if (user.getRole() != Role.WAREHOUSE && user.getRole() != Role.MANAGER) {
            throw error(HttpStatus.FORBIDDEN, "Outbound operations require warehouse or manager role");
        }
        return user;
    }

    private void validateQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw error(HttpStatus.BAD_REQUEST, "Actual quantity must be positive");
        }
        BigDecimal normalized = quantity.stripTrailingZeros();
        int scale = Math.max(normalized.scale(), 0);
        int integerDigits = Math.max(normalized.precision() - normalized.scale(), 0);
        if (scale > 3 || integerDigits > 15) {
            throw error(HttpStatus.BAD_REQUEST, "Actual quantity supports up to 15 integer and 3 fraction digits");
        }
    }

    private OutboundResponse response(OutboundRecord record) {
        return new OutboundResponse(record.getId(), record.getRecordNo(), record.getOrder().getId(),
                record.getOrder().getOrderNo(), record.getOrderItem().getId(), record.getProduct().getId(),
                record.getProduct().getSku(), record.getProduct().getName(), record.getProduct().getUnit(),
                record.getPlannedQuantity(), record.getActualQuantity(), record.getStatus(),
                record.getDifferenceReason(), record.getOperator() == null ? null : record.getOperator().getUsername(),
                record.getCompletedAt(), record.getCreatedAt(), record.getUpdatedAt());
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private String quantity(BigDecimal value) { return value.setScale(3).toPlainString(); }
    private ResponseStatusException error(HttpStatus status, String reason) {
        return new ResponseStatusException(status, reason);
    }
}
