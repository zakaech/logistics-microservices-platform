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

        @NotBlank(message = "email est obligatoire")
        @Email(message = "email doit être une adresse correctement formée")
        @Size(max = 180, message = "email ne doit pas dépasser 180 caractères")
        String email,

        @NotBlank(message = "password est obligatoire")
        @Size(min = 10, max = 100, message = "password doit comporter entre 10 et 100 caractères")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "password doit contenir une minuscule, une majuscule et un chiffre")
        String password,

        @NotBlank(message = "firstName est obligatoire")
        @Size(max = 80, message = "firstName ne doit pas dépasser 80 caractères")
        String firstName,

        @NotBlank(message = "lastName est obligatoire")
        @Size(max = 80, message = "lastName ne doit pas dépasser 80 caractères")
        String lastName) {
}
