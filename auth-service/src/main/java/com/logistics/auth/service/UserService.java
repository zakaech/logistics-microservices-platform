package com.logistics.auth.service;

import com.logistics.auth.domain.enums.RoleName;
import com.logistics.auth.dto.response.PagedResponse;
import com.logistics.auth.dto.response.UserResponse;
import org.springframework.data.domain.Pageable;

import java.util.Set;
import java.util.UUID;

/** Read and administration operations on user accounts. */
public interface UserService {

    UserResponse findById(UUID id);

    PagedResponse<UserResponse> search(String term, Pageable pageable);

    /** Replaces the whole role set. Administrator-only, enforced at the controller. */
    UserResponse replaceRoles(UUID id, Set<RoleName> roles);

    UserResponse changeStatus(UUID id, boolean enabled);
}
