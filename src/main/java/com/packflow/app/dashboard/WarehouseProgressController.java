package com.packflow.app.dashboard;

import com.packflow.app.dashboard.WarehouseProgressService.WarehouseProgressResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class WarehouseProgressController {
    private final WarehouseProgressService service;

    public WarehouseProgressController(WarehouseProgressService service) {
        this.service = service;
    }

    @GetMapping("/warehouse-progress")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'MANAGER')")
    public WarehouseProgressResponse warehouseProgress() {
        return service.today();
    }
}
