package com.logistics.auth.dto.request;

import jakarta.validation.constraints.NotNull;

/** Enables or disables a user. Boxed Boolean so that a missing field fails validation. */
public record UpdateUserStatusRequest(

        @NotNull(message = "enabled est obligatoire")
        Boolean enabled) {
}
