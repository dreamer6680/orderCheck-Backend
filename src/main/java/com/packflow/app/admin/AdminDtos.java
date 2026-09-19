package com.packflow.app.admin;

import com.packflow.app.user.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AdminDtos {
    private AdminDtos() { }

    public record CreateUserRequest(
            @NotBlank @Size(max = 50)
            @Pattern(regexp = "[A-Za-z0-9._-]{3,50}", message = "Username contains unsupported characters")
            String username,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 100) String displayName,
            @NotNull Role role,
            @NotNull Boolean enabled) { }

    public record UpdateUserRequest(
            @NotBlank @Size(max = 100) String displayName,
            @NotNull Role role,
            @NotNull Boolean enabled) { }

    public record ResetPasswordRequest(
            @NotBlank @Size(min = 8, max = 72) String newPassword) { }

    public record UserResponse(
            Long id,
            String username,
            String displayName,
            Role role,
            boolean enabled) { }

    public record RoleResponse(
            Role role,
            String label,
            String description) { }
}
