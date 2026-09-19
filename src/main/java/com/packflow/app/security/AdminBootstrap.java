package com.packflow.app.security;

import com.packflow.app.user.AppUser;
import com.packflow.app.user.AppUserRepository;
import com.packflow.app.user.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local-only demo administrator. The username is always "admin"; the password
 * is read from app.demo.admin-password in application.yaml.
 */
@Component
@Profile("local")
public class AdminBootstrap implements ApplicationRunner {
    private static final String DEMO_USERNAME = "admin";

    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final String password;

    public AdminBootstrap(AppUserRepository users, PasswordEncoder passwords,
            @Value("${app.demo.admin-password:}") String password) {
        this.users = users;
        this.passwords = passwords;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (password.isBlank()) {
            // Existing databases and automated tests can run without a demo administrator.
            return;
        }
        if (password.length() < 6 || password.length() > 72) {
            throw new IllegalStateException("app.demo.admin-password must contain 6-72 characters");
        }

        var existing = users.findByUsername(DEMO_USERNAME);
        if (existing.isEmpty()) {
            users.saveAndFlush(new AppUser(DEMO_USERNAME, passwords.encode(password),
                    "Demo Administrator", Role.MANAGER, true));
            return;
        }

        AppUser admin = existing.orElseThrow();
        if (!admin.isEnabled() || admin.getRole() != Role.MANAGER) {
            throw new IllegalStateException(
                    "Existing 'admin' account is disabled or not a manager; resolve it in user management");
        }
        if (!passwords.matches(password, admin.getPasswordHash())) {
            // A local password change takes effect on restart and invalidates previous JWTs.
            admin.resetPassword(passwords.encode(password));
        }
    }
}
