package com.packflow.app.security;

import com.packflow.app.user.AppUser;
import com.packflow.app.user.AppUserRepository;
import com.packflow.app.user.Role;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class DemoUsersBootstrapTest {
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final DefaultApplicationArguments args = new DefaultApplicationArguments();

    private DemoUsersBootstrap bootstrap() {
        return new DemoUsersBootstrap(users, encoder, "sales123", "warehouse123", "manager123");
    }

    @Test
    void createsExactlyTheThreeEnabledRoleAccountsForNewDatabase() {
        bootstrap().run(args);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(users, times(3)).saveAndFlush(captor.capture());
        List<AppUser> created = captor.getAllValues();
        assertThat(created).extracting(AppUser::getUsername)
                .containsExactly("sales", "warehouse", "manager");
        assertThat(created).extracting(AppUser::getRole)
                .containsExactly(Role.SALES, Role.WAREHOUSE, Role.MANAGER);
        assertThat(created).allMatch(AppUser::isEnabled);
        assertThat(encoder.matches("sales123", created.get(0).getPasswordHash())).isTrue();
        assertThat(encoder.matches("warehouse123", created.get(1).getPasswordHash())).isTrue();
        assertThat(encoder.matches("manager123", created.get(2).getPasswordHash())).isTrue();
    }

    @Test
    void enablesOldSeededAccountsAndUpdatesTheirLocalPasswords() {
        AppUser sales = new AppUser("sales", encoder.encode("old-sales"), "Sales",
                Role.SALES, false);
        AppUser warehouse = new AppUser("warehouse", encoder.encode("old-warehouse"), "Warehouse",
                Role.WAREHOUSE, false);
        AppUser manager = new AppUser("manager", encoder.encode("old-manager"), "Manager",
                Role.MANAGER, false);
        when(users.findByUsername("sales")).thenReturn(Optional.of(sales));
        when(users.findByUsername("warehouse")).thenReturn(Optional.of(warehouse));
        when(users.findByUsername("manager")).thenReturn(Optional.of(manager));

        bootstrap().run(args);

        assertThat(List.of(sales, warehouse, manager)).allMatch(AppUser::isEnabled);
        assertThat(encoder.matches("sales123", sales.getPasswordHash())).isTrue();
        assertThat(encoder.matches("warehouse123", warehouse.getPasswordHash())).isTrue();
        assertThat(encoder.matches("manager123", manager.getPasswordHash())).isTrue();
        assertThat(List.of(sales, warehouse, manager))
                .allMatch(user -> user.getTokenVersion() == 2L);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void leavesAlreadyConfiguredAccountsUnchanged() {
        AppUser sales = new AppUser("sales", encoder.encode("sales123"), "Sales", Role.SALES, true);
        AppUser warehouse = new AppUser("warehouse", encoder.encode("warehouse123"),
                "Warehouse", Role.WAREHOUSE, true);
        AppUser manager = new AppUser("manager", encoder.encode("manager123"),
                "Manager", Role.MANAGER, true);
        when(users.findByUsername("sales")).thenReturn(Optional.of(sales));
        when(users.findByUsername("warehouse")).thenReturn(Optional.of(warehouse));
        when(users.findByUsername("manager")).thenReturn(Optional.of(manager));

        bootstrap().run(args);

        assertThat(List.of(sales, warehouse, manager)).allMatch(user -> user.getTokenVersion() == 0L);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void refusesToElevateAReusedUsernameWithDifferentRole() {
        AppUser conflicting = new AppUser("sales", encoder.encode("old"),
                "Existing user", Role.WAREHOUSE, false);
        when(users.findByUsername("sales")).thenReturn(Optional.of(conflicting));

        assertThatThrownBy(() -> bootstrap().run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sales");
        assertThat(conflicting.isEnabled()).isFalse();
    }

    @Test
    void supportsExplicitlyDisabledDemoBootstrap() {
        new DemoUsersBootstrap(users, encoder, "", "", "").run(args);
        verify(users, never()).findByUsername(any());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void requiresAllThreeValidPasswords() {
        assertThatThrownBy(() -> new DemoUsersBootstrap(users, encoder,
                "sales123", "", "manager123").run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("all three");
        assertThatThrownBy(() -> new DemoUsersBootstrap(users, encoder,
                "12345", "warehouse123", "manager123").run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("6-72");
    }
}
