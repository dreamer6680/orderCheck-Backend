package com.packflow.app.user;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);
    boolean existsByUsernameIgnoreCase(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from AppUser user where user.role = com.packflow.app.user.Role.MANAGER "
            + "and user.enabled = true order by user.id")
    List<AppUser> findActiveManagersForUpdate();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from AppUser user where user.id = :id")
    Optional<AppUser> findByIdForUpdate(@Param("id") Long id);

    @Query("select user from AppUser user "
            + "where (:role is null or user.role = :role) "
            + "and (:enabled is null or user.enabled = :enabled) "
            + "and (lower(user.username) like :pattern or lower(user.displayName) like :pattern) "
            + "order by user.id asc")
    List<AppUser> search(@Param("role") Role role, @Param("enabled") Boolean enabled,
            @Param("pattern") String pattern, Pageable pageable);
}
