package com.logistics.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Login payload. The password is not length-checked here: rejecting a short password with a
 * validation error would tell an attacker their guess was malformed rather than simply wrong.
 */
public record LoginRequest(

        @NotBlank(message = "email est obligatoire")
        String email,

        @NotBlank(message = "password est obligatoire")
        String password) {
}
