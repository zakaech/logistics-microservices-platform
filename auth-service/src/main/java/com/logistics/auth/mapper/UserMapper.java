package com.logistics.auth.mapper;

import com.logistics.auth.domain.entity.Role;
import com.logistics.auth.domain.entity.User;
import com.logistics.auth.domain.enums.RoleName;
import com.logistics.auth.dto.response.UserResponse;
import org.mapstruct.Mapper;

/**
 * Entity to DTO translation.
 *
 * <p>The build is configured with {@code unmappedTargetPolicy=ERROR}: adding a field to
 * {@link UserResponse} without saying where it comes from breaks compilation rather than silently
 * shipping a null. Nothing maps {@code passwordHash}, and nothing ever will.
 */
@Mapper
public interface UserMapper {

    UserResponse toResponse(User user);

    /** Element mapping used to turn {@code Set<Role>} into {@code Set<RoleName>}. */
    default RoleName toRoleName(Role role) {
        return role.getName();
    }
}
