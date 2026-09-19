package com.packflow.app.admin;

import com.packflow.app.admin.AdminDtos.CreateUserRequest;
import com.packflow.app.admin.AdminDtos.ResetPasswordRequest;
import com.packflow.app.admin.AdminDtos.RoleResponse;
import com.packflow.app.admin.AdminDtos.UpdateUserRequest;
import com.packflow.app.admin.AdminDtos.UserResponse;
import com.packflow.app.security.JwtPrincipal;
import com.packflow.app.user.Role;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('MANAGER')")
public class AdminUserController {
    private final AdminUserService service;

    public AdminUserController(AdminUserService service) { this.service = service; }

    @GetMapping("/roles")
    public List<RoleResponse> roles(Authentication authentication) {
        return service.roles(username(authentication));
    }

    @GetMapping("/users")
    public List<UserResponse> list(@RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            Authentication authentication) {
        return service.list(role, enabled, keyword, page, size, username(authentication));
    }

    @GetMapping("/users/{id}")
    public UserResponse get(@PathVariable Long id, Authentication authentication) {
        return service.get(id, username(authentication));
    }

    @PostMapping("/users")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request,
            Authentication authentication) {
        UserResponse response = service.create(request, username(authentication));
        return ResponseEntity.created(URI.create("/api/admin/users/" + response.id())).body(response);
    }

    @PutMapping("/users/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request,
            Authentication authentication) {
        return service.update(id, request, username(authentication));
    }

    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id,
            @Valid @RequestBody ResetPasswordRequest request, Authentication authentication) {
        service.resetPassword(id, request.newPassword(), username(authentication));
        return ResponseEntity.noContent().build();
    }

    private String username(Authentication authentication) {
        return authentication.getPrincipal() instanceof JwtPrincipal principal
                ? principal.username() : authentication.getName();
    }
}
