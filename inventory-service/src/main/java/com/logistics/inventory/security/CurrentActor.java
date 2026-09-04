package com.logistics.inventory.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Who is performing the current operation, for the movement ledger.
 *
 * <p>Read from the verified token, never from a request field or a gateway header: an audit trail
 * whose author can be chosen by the caller records nothing worth having.
 *
 * <p>For a technical account the subject is the client id ({@code order-service}), which is exactly
 * what should appear against a movement caused by the order saga.
 */
@Component
public class CurrentActor {

    private static final String SYSTEM = "system";

    public String identifier() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            // Reached only by the scheduled sweeper, which runs outside any request.
            return SYSTEM;
        }
        return authentication.getName();
    }
}
