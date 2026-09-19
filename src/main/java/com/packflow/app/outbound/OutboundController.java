package com.packflow.app.outbound;

import com.packflow.app.outbound.OutboundDtos.CompleteRequest;
import com.packflow.app.outbound.OutboundDtos.ScheduleRequest;
import com.packflow.app.outbound.OutboundDtos.OutboundCheckResponse;
import com.packflow.app.outbound.OutboundDtos.OutboundResponse;
import com.packflow.app.security.JwtPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/outbound-records")
@PreAuthorize("hasAnyRole('WAREHOUSE', 'MANAGER')")
public class OutboundController {
    private final OutboundService service;

    public OutboundController(OutboundService service) { this.service = service; }

    @GetMapping
    public List<OutboundResponse> list(@RequestParam(required = false) OutboundStatus status,
            @RequestParam(required = false) LocalDate deliveryDate, Authentication authentication) {
        return service.list(status, deliveryDate, username(authentication));
    }

    @GetMapping("/{id}")
    public OutboundResponse detail(@PathVariable Long id, Authentication authentication) {
        return service.detail(id, username(authentication));
    }

    @GetMapping("/{id}/check-inventory")
    public OutboundCheckResponse checkInventory(@PathVariable Long id, Authentication authentication) {
        return service.checkInventory(id, username(authentication));
    }

    @PostMapping("/{id}/complete")
    public OutboundResponse complete(@PathVariable Long id, @Valid @RequestBody CompleteRequest request,
            Authentication authentication) {
        return service.complete(id, request.actualQuantity(), request.differenceReason(), username(authentication));
    }

    @PatchMapping("/{id}/schedule")
    public OutboundResponse reschedule(@PathVariable Long id, @Valid @RequestBody ScheduleRequest request,
            Authentication authentication) {
        return service.reschedule(id, request.plannedOutboundDate(), username(authentication));
    }

    @PostMapping("/{id}/cancel")
    public OutboundResponse cancel(@PathVariable Long id, Authentication authentication) {
        return service.cancel(id, username(authentication));
    }

    private String username(Authentication authentication) {
        return authentication.getPrincipal() instanceof JwtPrincipal principal
                ? principal.username() : authentication.getName();
    }
}
