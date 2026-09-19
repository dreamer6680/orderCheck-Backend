package com.packflow.app.admin;

import com.packflow.app.admin.AdminDtos.CreateUserRequest;
import com.packflow.app.admin.AdminDtos.RoleResponse;
import com.packflow.app.admin.AdminDtos.UpdateUserRequest;
import com.packflow.app.admin.AdminDtos.UserResponse;
import com.packflow.app.user.AppUser;
import com.packflow.app.user.AppUserRepository;
import com.packflow.app.user.Role;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminUserService {
    private final AppUserRepository users;
    private final PasswordEncoder passwords;

    public AdminUserService(AppUserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> roles(String actorUsername) {
        requireManager(actorUsername);
        return List.of(
                new RoleResponse(Role.SALES, "销售", "创建客户订单、核查库存并查看订单状态"),
                new RoleResponse(Role.WAREHOUSE, "仓库", "登记入库、执行或取消出库并查看库存"),
                new RoleResponse(Role.MANAGER, "管理员", "执行全部业务操作并管理用户与角色"));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list(Role role, Boolean enabled, String keyword, int page, int size,
            String actorUsername) {
        requireManager(actorUsername);
        if (page < 0 || size < 1 || size > 200) {
            throw error(HttpStatus.BAD_REQUEST, "Invalid page or size (1-200)");
        }
        String pattern = "%" + normalizeKeyword(keyword) + "%";
        return users.search(role, enabled, pattern, PageRequest.of(page, size)).stream()
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id, String actorUsername) {
        requireManager(actorUsername);
        return response(users.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "User not found")));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request, String actorUsername) {
        requireManager(actorUsername);
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsernameIgnoreCase(username)) {
            throw error(HttpStatus.CONFLICT, "Username already exists");
        }
        AppUser user = new AppUser(username, passwords.encode(request.password()), request.displayName().trim(),
                request.role(), request.enabled());
        try {
            return response(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            throw error(HttpStatus.CONFLICT, "Username already exists");
        }
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request, String actorUsername) {
        List<AppUser> activeManagers = users.findActiveManagersForUpdate();
        AppUser actor = activeManagers.stream()
                .filter(user -> user.getUsername().equals(actorUsername))
                .findFirst().orElseThrow(() -> error(HttpStatus.FORBIDDEN, "An active manager is required"));
        AppUser target = users.findByIdForUpdate(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "User not found"));
        boolean remainsActiveManager = request.enabled() && request.role() == Role.MANAGER;
        if (target.getId().equals(actor.getId()) && !remainsActiveManager) {
            throw error(HttpStatus.CONFLICT, "A manager cannot disable or demote their own account");
        }
        if (target.isEnabled() && target.getRole() == Role.MANAGER && !remainsActiveManager
                && activeManagers.size() <= 1) {
            throw error(HttpStatus.CONFLICT, "At least one active manager is required");
        }
        target.updateProfile(request.displayName().trim(), request.role(), request.enabled());
        return response(target);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword, String actorUsername) {
        requireManager(actorUsername);
        AppUser target = users.findByIdForUpdate(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "User not found"));
        target.resetPassword(passwords.encode(newPassword));
    }

    private AppUser requireManager(String username) {
        AppUser actor = users.findByUsername(username).filter(AppUser::isEnabled)
                .orElseThrow(() -> error(HttpStatus.FORBIDDEN, "An active manager is required"));
        if (actor.getRole() != Role.MANAGER) {
            throw error(HttpStatus.FORBIDDEN, "An active manager is required");
        }
        return actor;
    }

    private UserResponse response(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(),
                user.isEnabled());
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
    }

    private ResponseStatusException error(HttpStatus status, String reason) {
        return new ResponseStatusException(status, reason);
    }
}
