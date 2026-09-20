package com.packflow.app.security;

import com.packflow.app.user.AppUser;
import com.packflow.app.user.AppUserRepository;
import com.packflow.app.user.Role;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local/demo profile only: activate the three original role accounts on both
 * fresh databases (B8) and existing databases (V2 seed disabled by V4).
 * Passwords are sourced from application.yaml's local section and may be
 * overridden by environment variables. Never execute this in production.
 */
@Component
@Profile("local")
public class DemoUsersBootstrap implements ApplicationRunner {

    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final List<DemoAccount> accounts;

    public DemoUsersBootstrap(AppUserRepository users, PasswordEncoder passwords,
            @Value("${app.demo.sales-password:}") String salesPassword,
            @Value("${app.demo.warehouse-password:}") String warehousePassword,
            @Value("${app.demo.manager-password:}") String managerPassword) {
        this.users = users;
        this.passwords = passwords;
        this.accounts = List.of(
                new DemoAccount("sales", "Sales", Role.SALES, salesPassword),
                new DemoAccount("warehouse", "Warehouse", Role.WAREHOUSE, warehousePassword),
                new DemoAccount("manager", "Manager", Role.MANAGER, managerPassword));
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // An empty configuration explicitly disables this optional demo bootstrap.
        if (accounts.stream().allMatch(account -> account.password().isBlank())) return;
        if (accounts.stream().anyMatch(account -> account.password().isBlank())) {
            throw new IllegalStateException("Configure all three app.demo.*-password values or none");
        }
        for (DemoAccount account : accounts) {
            if (account.password().length() < 6 || account.password().length() > 72) {
                throw new IllegalStateException("Demo password must contain 6-72 characters: " + account.username());
            }
        }
        for (DemoAccount account : accounts) {
            var existing = users.findByUsername(account.username());
            if (existing.isEmpty()) {
                users.saveAndFlush(new AppUser(account.username(), passwords.encode(account.password()),
                        account.displayName(), account.role(), true));
                continue;
            }
            AppUser user = existing.orElseThrow();
            if (user.getRole() != account.role()) {
                throw new IllegalStateException("Existing demo username has a different role: "
                        + account.username() + "; resolve the conflict in user management");
            }
            if (!user.isEnabled()) {
                user.updateProfile(user.getDisplayName(), account.role(), true);
            }
            // Local demo accounts deliberately adopt the configured password after each restart.
            // resetPassword increments tokenVersion so old sessions are invalidated.
            if (!passwords.matches(account.password(), user.getPasswordHash())) {
                user.resetPassword(passwords.encode(account.password()));
            }
        }
    }

    private record DemoAccount(String username, String displayName, Role role, String password) { }
}
