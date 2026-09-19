package com.packflow.app.security;

import com.packflow.app.user.AppUser;
import com.packflow.app.user.AppUserRepository;
import com.packflow.app.user.Role;
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
import static org.mockito.Mockito.when;

class AdminBootstrapTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final DefaultApplicationArguments args = new DefaultApplicationArguments();

    @Test
    void createsAdminWithPasswordFromYaml() {
        when(users.findByUsername("admin")).thenReturn(Optional.empty());

        new AdminBootstrap(users, encoder, "123456").run(args);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(users).saveAndFlush(captor.capture());
        AppUser admin = captor.getValue();
        assertThat(admin.getUsername()).isEqualTo("admin");
        assertThat(admin.getRole()).isEqualTo(Role.MANAGER);
        assertThat(admin.isEnabled()).isTrue();
        assertThat(encoder.matches("123456", admin.getPasswordHash())).isTrue();
    }

    @Test
    void passwordIsOptionalWhenDemoAdminIsNotNeeded() {
        new AdminBootstrap(users, encoder, "").run(args);
        verify(users, never()).findByUsername(any());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void existingManagerPasswordIsKeptIfItMatches() {
        AppUser admin = new AppUser("admin", encoder.encode("my-local-password"),
                "Demo Administrator", Role.MANAGER, true);
        when(users.findByUsername("admin")).thenReturn(Optional.of(admin));

        new AdminBootstrap(users, encoder, "my-local-password").run(args);

        assertThat(admin.getTokenVersion()).isZero();
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void changingDemoPasswordRevokesExistingTokens() {
        AppUser admin = new AppUser("admin", encoder.encode("old-local-password"),
                "Demo Administrator", Role.MANAGER, true);
        when(users.findByUsername("admin")).thenReturn(Optional.of(admin));

        new AdminBootstrap(users, encoder, "new-local-password").run(args);

        assertThat(encoder.matches("new-local-password", admin.getPasswordHash())).isTrue();
        assertThat(admin.getTokenVersion()).isEqualTo(1);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void doesNotElevateAnExistingAccountWithTheAdminUsername() {
        AppUser sales = new AppUser("admin", encoder.encode("sales-password"),
                "Sales", Role.SALES, true);
        when(users.findByUsername("admin")).thenReturn(Optional.of(sales));

        assertThatThrownBy(() -> new AdminBootstrap(users, encoder, "my-local-password").run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Existing 'admin' account");
        assertThat(sales.getRole()).isEqualTo(Role.SALES);
    }

    @Test
    void rejectsInvalidDemoPasswordLengths() {
        assertThatThrownBy(() -> new AdminBootstrap(users, encoder, "12345").run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("6-72");
        assertThatThrownBy(() -> new AdminBootstrap(users, encoder, "x".repeat(73)).run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("6-72");
    }
}
