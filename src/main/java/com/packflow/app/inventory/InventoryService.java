package com.packflow.app.inventory;

import com.packflow.app.product.Product;
import com.packflow.app.product.ProductRepository;
import com.packflow.app.user.AppUser;
import com.packflow.app.user.AppUserRepository;
import com.packflow.app.user.Role;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InboundRecordRepository inboundRecordRepository;
    private final ProductRepository productRepository;
    private final AppUserRepository appUserRepository;

    public InventoryService(
            InventoryRepository inventoryRepository,
            InboundRecordRepository inboundRecordRepository,
            ProductRepository productRepository,
            AppUserRepository appUserRepository) {
        this.inventoryRepository = inventoryRepository;
        this.inboundRecordRepository = inboundRecordRepository;
        this.productRepository = productRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public InboundResult recordInbound(Long productId, BigDecimal quantity, String remark, String operatorUsername) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inbound quantity must be positive");
        }

        AppUser operator = appUserRepository.findByUsername(operatorUsername)
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown operator"));
        if (operator.getRole() != Role.WAREHOUSE && operator.getRole() != Role.MANAGER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Operator cannot record inbound inventory");
        }

        if (!productRepository.existsById(productId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory not found"));

        Product product = inventory.getProduct();
        InboundRecord record = inboundRecordRepository.save(new InboundRecord(
                nextRecordNo(), product, quantity, remark, operator));
        inventory.recordInbound(record);

        return new InboundResult(record.getId(), record.getRecordNo(), productId, quantity, remark, operatorUsername);
    }

    @Transactional(readOnly = true)
    public List<InventoryProjection> listInventory() {
        return inventoryRepository.findAll().stream()
                .map(this::toProjection)
                .toList();
    }

    @Transactional(readOnly = true)
    public InventoryProjection inventoryForProduct(Long productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory not found"));
        return toProjection(inventory);
    }

    @Transactional(readOnly = true)
    public List<InboundResult> listInboundRecords(Long productId) {
        List<InboundRecord> records = productId == null
                ? inboundRecordRepository.findAllByOrderByCreatedAtDesc()
                : inboundRecordRepository.findByProductId(productId);
        return records.stream()
                .map(record -> new InboundResult(
                        record.getId(),
                        record.getRecordNo(),
                        record.getProduct().getId(),
                        record.getQuantity(),
                        record.getRemark(),
                        record.getOperator().getUsername()))
                .toList();
    }

    private InventoryProjection toProjection(Inventory inventory) {
        BigDecimal physicalQuantity = inventory.getQuantity();
        BigDecimal pendingQuantity = inventoryRepository.pendingOutboundQuantity(inventory.getProduct().getId());
        if (pendingQuantity == null) {
            pendingQuantity = BigDecimal.ZERO;
        }
        return new InventoryProjection(
                inventory.getProduct().getId(),
                inventory.getProduct().getSku(),
                inventory.getProduct().getName(),
                inventory.getProduct().getUnit(),
                physicalQuantity,
                pendingQuantity,
                physicalQuantity.subtract(pendingQuantity));
    }

    private String nextRecordNo() {
        return "IN-" + UUID.randomUUID().toString().replace("-", "");
    }

    public record InboundResult(
            Long id,
            String recordNo,
            Long productId,
            BigDecimal quantity,
            String remark,
            String operatorUsername) {
    }

    public record InventoryProjection(
            Long productId,
            String sku,
            String productName,
            String unit,
            BigDecimal physicalQuantity,
            BigDecimal pendingQuantity,
            BigDecimal availableQuantity) {
    }
}
