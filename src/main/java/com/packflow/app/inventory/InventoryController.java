package com.packflow.app.inventory;

import com.packflow.app.security.JwtPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'MANAGER')")
    public List<InventoryService.InventoryProjection> listInventory() {
        return inventoryService.listInventory();
    }

    /** SALES can read stock quantities, but not other tasks' reserved quantities. */
    @GetMapping("/quantities")
    @PreAuthorize("hasAnyRole('SALES', 'WAREHOUSE', 'MANAGER')")
    public List<InventoryQuantity> listQuantities() {
        return inventoryService.listInventory().stream()
                .map(item -> new InventoryQuantity(item.productId(), item.sku(), item.productName(),
                        item.unit(), item.physicalQuantity(), item.availableQuantity()))
                .toList();
    }

    public record InventoryQuantity(Long productId, String sku, String productName, String unit,
            BigDecimal physicalQuantity, BigDecimal availableQuantity) { }

    @GetMapping("/{productId}")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'MANAGER')")
    public InventoryService.InventoryProjection inventoryForProduct(@PathVariable Long productId) {
        return inventoryService.inventoryForProduct(productId);
    }

    @GetMapping("/inbounds")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'MANAGER')")
    public List<InventoryService.InboundResult> listInboundRecords(@RequestParam(required = false) Long productId) {
        return inventoryService.listInboundRecords(productId);
    }

    @PostMapping("/inbounds")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'MANAGER')")
    public ResponseEntity<InventoryService.InboundResult> recordInbound(
            @Valid @RequestBody InboundRequest request,
            Authentication authentication) {
        InventoryService.InboundResult result = inventoryService.recordInbound(
                request.productId(), request.quantity(), request.remark(), usernameOf(authentication));
        return ResponseEntity.created(URI.create("/api/inventory/inbounds/" + result.id())).body(result);
    }

    private String usernameOf(Authentication authentication) {
        if (authentication.getPrincipal() instanceof JwtPrincipal principal) {
            return principal.username();
        }
        return authentication.getName();
    }

    public record InboundRequest(
            @NotNull Long productId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 3) BigDecimal quantity,
            @Size(max = 500) String remark) {
    }
}
