package com.packflow.app.order;

import com.packflow.app.order.OrderDtos.CreateOrderRequest;
import com.packflow.app.order.OrderDtos.DeliveryDateRequest;
import com.packflow.app.order.OrderDtos.UnableToDeliverRequest;
import com.packflow.app.order.OrderDtos.PartialOutboundRequest;
import com.packflow.app.order.OrderDtos.SupplementalRequest;
import com.packflow.app.order.OrderDtos.AcceptShortDeliveryRequest;
import com.packflow.app.order.OrderDtos.OrderResponse;
import com.packflow.app.security.JwtPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@PreAuthorize("hasAnyRole('SALES', 'MANAGER')")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) { this.service = service; }

    @GetMapping
    public List<OrderResponse> list(@RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String keyword, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size, Authentication authentication) {
        return service.listOrders(status, keyword, page, size, username(authentication));
    }

    @GetMapping("/abnormal")
    public List<OrderResponse> abnormal(@RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size,
            Authentication authentication) {
        return service.listOrders(OrderStatus.ABNORMAL, keyword, page, size, username(authentication));
    }

    @GetMapping("/{id}")
    public OrderResponse detail(@PathVariable Long id, Authentication authentication) {
        return service.getOrder(id, username(authentication));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SALES', 'MANAGER')")
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request, Authentication authentication) {
        OrderResponse result = service.createOrder(request, username(authentication));
        return ResponseEntity.created(URI.create("/api/orders/" + result.id())).body(result);
    }

    @PatchMapping("/{id}/delivery-date")
    public OrderResponse updateDeliveryDate(@PathVariable Long id,
            @Valid @RequestBody DeliveryDateRequest request, Authentication authentication) {
        return service.changeDeliveryDate(id, request.deliveryDate(), username(authentication));
    }

    @PostMapping("/{id}/unable-to-deliver")
    public OrderResponse markUnableToDeliver(@PathVariable Long id,
            @Valid @RequestBody UnableToDeliverRequest request, Authentication authentication) {
        return service.markUnableToDeliver(id, request.reason(), username(authentication));
    }

    @PostMapping("/{id}/partial-outbound")
    public OrderResponse planPartialOutbound(@PathVariable Long id,
            @Valid @RequestBody PartialOutboundRequest request, Authentication authentication) {
        return service.planPartialOutbound(id, request.customerAgreed(), username(authentication));
    }

    @PostMapping("/{id}/supplemental-outbound")
    public OrderResponse planSupplemental(@PathVariable Long id,
            @Valid @RequestBody SupplementalRequest request, Authentication authentication) {
        return service.planSupplemental(id, request.orderItemId(), request.quantity(), username(authentication));
    }

    @PostMapping("/{id}/accept-short-delivery")
    public OrderResponse acceptShortDelivery(@PathVariable Long id,
            @Valid @RequestBody AcceptShortDeliveryRequest request, Authentication authentication) {
        return service.acceptShortDelivery(id, request.reason(), username(authentication));
    }

    @PostMapping("/{id}/check-inventory")
    @PreAuthorize("hasAnyRole('SALES', 'MANAGER')")
    public OrderResponse check(@PathVariable Long id, Authentication authentication) {
        return service.checkInventory(id, username(authentication));
    }

    @PostMapping("/{id}/recheck-inventory")
    @PreAuthorize("hasAnyRole('SALES', 'MANAGER')")
    public OrderResponse recheck(@PathVariable Long id, Authentication authentication) {
        return service.recheckInventory(id, username(authentication));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SALES', 'MANAGER')")
    public OrderResponse cancel(@PathVariable Long id, Authentication authentication) {
        return service.cancelOrder(id, username(authentication));
    }

    private String username(Authentication authentication) {
        return authentication.getPrincipal() instanceof JwtPrincipal principal ? principal.username() : authentication.getName();
    }
}
