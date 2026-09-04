package com.logistics.auth.dto.response;

import com.logistics.auth.domain.enums.RoleName;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Public view of a user.
 *
 * <p>{@code passwordHash} has no counterpart here, and the mapper is configured to fail the build on
 * an unmapped target - so the hash cannot leak through an accidental field addition.
 */
public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        Set<RoleName> roles,
        Instant createdAt) {
}
