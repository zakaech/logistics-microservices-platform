package com.logistics.auth.service.impl;

import com.logistics.auth.domain.entity.Role;
import com.logistics.auth.domain.entity.User;
import com.logistics.auth.domain.enums.RoleName;
import com.logistics.auth.dto.response.PagedResponse;
import com.logistics.auth.dto.response.UserResponse;
import com.logistics.auth.exception.ResourceNotFoundException;
import com.logistics.auth.mapper.UserMapper;
import com.logistics.auth.repository.RefreshTokenRepository;
import com.logistics.auth.repository.RoleRepository;
import com.logistics.auth.repository.UserRepository;
import com.logistics.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        return userMapper.toResponse(loadUser(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> search(String term, Pageable pageable) {
        // Two questions, two queries: passing a null term into the search query would bind an
        // untyped null that PostgreSQL cannot use in lower().
        Page<User> page = (term == null || term.isBlank())
                ? userRepository.findAll(pageable)
                : userRepository.search(term.trim(), pageable);

        return PagedResponse.from(page, userMapper::toResponse);
    }

    @Override
    @Transactional
    public UserResponse replaceRoles(UUID id, Set<RoleName> roleNames) {
        User user = loadUser(id);
        Set<Role> roles = roleRepository.findByNameIn(roleNames);

        if (roles.size() != roleNames.size()) {
            throw new IllegalArgumentException("Un ou plusieurs rôles n'existent pas : " + roleNames);
        }

        user.replaceRoles(roles);
        User saved = userRepository.save(user);

        // Roles are baked into the access token, so an already-issued token would keep the old set
        // until it expires. Revoking the refresh tokens bounds that window to the access token TTL.
        int revoked = refreshTokenRepository.revokeAllForUser(saved);
        log.info("Replaced roles of user {} with {} and revoked {} refresh token(s)",
                id, roleNames, revoked);

        return userMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public UserResponse changeStatus(UUID id, boolean enabled) {
        User user = loadUser(id);
        user.setEnabled(enabled);
        User saved = userRepository.save(user);

        if (!enabled) {
            // Disabling must take effect now, not at the next token expiry.
            refreshTokenRepository.revokeAllForUser(saved);
        }

        log.info("User {} is now {}", id, enabled ? "enabled" : "disabled");
        return userMapper.toResponse(saved);
    }

    private User loadUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));
    }
}
