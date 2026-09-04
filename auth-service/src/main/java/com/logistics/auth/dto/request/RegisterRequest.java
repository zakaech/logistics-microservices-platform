package com.logistics.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Self-registration payload.
 *
 * <p>There is no {@code roles} field on purpose: the granted role is decided by the server
 * ({@code ROLE_CLIENT}), never by the caller. A privilege escalation cannot be attempted on an input
 * that does not exist.
 */
public record RegisterRequest(

        @NotBlank(message = "email is required")
        @Email(message = "email must be a well-formed address")
        @Size(max = 180, message = "email must not exceed 180 characters")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 10, max = 100, message = "password must be between 10 and 100 characters")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "password must contain a lowercase letter, an uppercase letter and a digit")
        String password,

        @NotBlank(message = "firstName is required")
        @Size(max = 80, message = "firstName must not exceed 80 characters")
        String firstName,

        @NotBlank(message = "lastName is required")
        @Size(max = 80, message = "lastName must not exceed 80 characters")
        String lastName) {
}
