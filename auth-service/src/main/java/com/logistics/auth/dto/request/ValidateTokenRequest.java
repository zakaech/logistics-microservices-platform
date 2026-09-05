package com.logistics.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Token introspection payload. */
public record ValidateTokenRequest(

        @NotBlank(message = "token est obligatoire")
        String token) {
}
