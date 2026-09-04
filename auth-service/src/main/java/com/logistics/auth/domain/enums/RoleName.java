package com.logistics.auth.domain.enums;

/**
 * The roles the platform recognises.
 *
 * <p>The name carries the {@code ROLE_} prefix Spring Security expects, so the value stored in the
 * database, the value put in the {@code roles} JWT claim and the authority used by
 * {@code @PreAuthorize} are one and the same string - there is no translation layer to get wrong.
 *
 * <p>{@link #ROLE_SERVICE} is reserved for technical accounts performing service-to-service calls.
 * It can never be obtained through registration.
 */
public enum RoleName {

    ROLE_ADMIN,
    ROLE_WAREHOUSE_MANAGER,
    ROLE_CLIENT,
    ROLE_SERVICE;

    /** The role granted to every self-registered user. */
    public static final RoleName DEFAULT_REGISTRATION_ROLE = ROLE_CLIENT;
}
