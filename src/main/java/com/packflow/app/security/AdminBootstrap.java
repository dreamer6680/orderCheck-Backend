package com.packflow.app.security;

import com.packflow.app.user.AppUser;
import com.packflow.app.user.AppUserRepository;
import com.packflow.app.user.Role;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time, explicit manager bootstrap. Never enables the known demo accounts.
 * Set APP_BOOTSTRAP_ADMIN_USERNAME/PASSWORD on the first production startup.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final String username;
    private final String password;

    public AdminBootstrap(AppUserRepository users, PasswordEncoder passwords,
            @Value("${APP_BOOTSTRAP_ADMIN_USERNAME:}") String username,
            @Value("${APP_BOOTSTRAP_ADMIN_PASSWORD:}") String password) {
        this.users = users;
        this.passwords = passwords;
        this.username = username;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (username.isBlank() && password.isBlank()) {
            return;
        }
        if (!username.matches("[A-Za-z0-9._-]{3,50}") || password.length() < 12 || password.length() > 72) {
            throw new IllegalStateException("Invalid initial manager credentials configuration");
        }
        if (!users.findActiveManagersForUpdate().isEmpty()) {
            return;
        }
        String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new IllegalStateException("Initial manager username already exists; choose a distinct username");
        }
        users.saveAndFlush(new AppUser(normalizedUsername, passwords.encode(password),
                "Administrator", Role.MANAGER, true));
    }
}
