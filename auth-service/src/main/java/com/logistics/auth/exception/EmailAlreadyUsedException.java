package com.logistics.auth.exception;

/** Raised when a registration targets an email address that already exists. */
public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String email) {
        super("Un compte existe déjà pour '" + email + "'.");
    }
}
