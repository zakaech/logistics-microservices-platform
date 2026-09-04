package com.logistics.auth.exception;

/** Raised when a registration targets an email address that already exists. */
public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String email) {
        super("An account already exists for '" + email + "'.");
    }
}
