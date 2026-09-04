package com.logistics.order.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Who is calling, read from the verified token.
 *
 * <p>The customer id is the token subject, never a request field: taking it from the body would let
 * anyone place an order on someone else's account. "Privileged" means staff, who may read any
 * order; everyone else sees only their own.
 */
@Component
public class OrderCaller {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_WAREHOUSE_MANAGER = "ROLE_WAREHOUSE_MANAGER";

    public UUID customerId() {
        Authentication authentication = requireAuthentication();
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException e) {
            // A service token has a client id as its subject, not a UUID. Such a caller has no
            // orders of its own, and saying so plainly beats a confusing parse error.
            throw new IllegalArgumentException(
                    "This endpoint is for user accounts; the caller is not one.");
        }
    }

    /** True for staff, who may read and act on any order. */
    public boolean isPrivileged() {
        Authentication authentication = requireAuthentication();
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> ROLE_ADMIN.equals(role) || ROLE_WAREHOUSE_MANAGER.equals(role));
    }

    /** Identifier recorded against a status change, for the audit trail. */
    public String actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }

    private Authentication requireAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated caller in the security context.");
        }
        return authentication;
    }
}
