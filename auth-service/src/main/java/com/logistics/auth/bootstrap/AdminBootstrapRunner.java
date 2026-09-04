package com.logistics.auth.bootstrap;

import com.logistics.auth.config.SecurityProperties;
import com.logistics.auth.domain.entity.Role;
import com.logistics.auth.domain.entity.User;
import com.logistics.auth.domain.enums.RoleName;
import com.logistics.auth.repository.RoleRepository;
import com.logistics.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Creates the first administrator when the database has none.
 *
 * <p>Done here rather than in a Flyway migration for one reason: a migration would have to contain a
 * BCrypt hash, and a hash committed to the repository is a hard-coded credential shared by every
 * clone of the project. The credentials come from the environment instead, and nothing is created
 * when they are absent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private final SecurityProperties securityProperties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        SecurityProperties.BootstrapAdmin config = securityProperties.bootstrapAdmin();

        if (config == null || !config.enabled()) {
            return;
        }
        if (!config.isComplete()) {
            log.warn("Admin bootstrap is enabled but BOOTSTRAP_ADMIN_EMAIL or "
                    + "BOOTSTRAP_ADMIN_PASSWORD is missing: no administrator was created.");
            return;
        }

        String email = config.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            log.debug("Administrator '{}' already exists, nothing to do.", email);
            return;
        }

        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN)
                .orElseThrow(() -> new IllegalStateException(
                        "ROLE_ADMIN is missing; check the seed migration."));

        User admin = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(config.password()))
                .firstName("Platform")
                .lastName("Administrator")
                .enabled(true)
                .build();
        admin.addRole(adminRole);

        userRepository.save(admin);
        log.info("Created the bootstrap administrator '{}'. Change this password before any "
                + "deployment beyond local development.", email);
    }
}
