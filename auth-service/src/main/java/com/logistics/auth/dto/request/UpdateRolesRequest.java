package com.logistics.auth.dto.request;

import com.logistics.auth.domain.enums.RoleName;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

/** Replaces the whole role set of a user. Administrator-only operation. */
public record UpdateRolesRequest(

        @NotEmpty(message = "roles must contain at least one role")
        Set<RoleName> roles) {
}
