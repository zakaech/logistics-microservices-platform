package com.logistics.auth.dto.request;

import com.logistics.auth.domain.enums.RoleName;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

/** Replaces the whole role set of a user. Administrator-only operation. */
public record UpdateRolesRequest(

        @NotEmpty(message = "roles doit contenir au moins un rôle")
        Set<RoleName> roles) {
}
