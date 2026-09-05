package com.logistics.auth.exception;

/**
 * Raised when authentication fails, whatever the underlying cause: unknown email, wrong password or
 * disabled account.
 *
 * <p>Deliberately a single exception with a single message. Distinguishing "unknown user" from
 * "wrong password" would let an attacker enumerate valid accounts.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Email ou mot de passe invalide.");
    }
}
