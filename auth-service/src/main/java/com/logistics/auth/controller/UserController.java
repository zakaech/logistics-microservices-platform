package com.logistics.auth.controller;

import com.logistics.auth.dto.request.UpdateRolesRequest;
import com.logistics.auth.dto.request.UpdateUserStatusRequest;
import com.logistics.auth.dto.response.PagedResponse;
import com.logistics.auth.dto.response.UserResponse;
import com.logistics.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * User administration.
 *
 * <p>Authorisation is expressed on each method rather than in a central URL table: the rule sits
 * next to the code it guards, so a reviewer sees both at once. Ownership checks compare the path id
 * with {@code authentication.name}, which is the {@code sub} claim - that is, the user's id.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Consultation des comptes et administration des rôles")
public class UserController {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Profil de l'utilisateur authentifié")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userService.findById(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id.toString() == authentication.name")
    @Operation(summary = "Consulter un utilisateur ; un non-administrateur ne peut lire que son propre compte")
    public UserResponse getById(@PathVariable UUID id) {
        return userService.findById(id);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lister et rechercher les utilisateurs")
    public PagedResponse<UserResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // The page size is capped server-side: an unbounded `size` is a denial-of-service knob.
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        return userService.search(q, pageable);
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Remplacer les rôles d'un utilisateur")
    public UserResponse replaceRoles(@PathVariable UUID id,
                                     @Valid @RequestBody UpdateRolesRequest request) {
        return userService.replaceRoles(id, request.roles());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activer ou désactiver un utilisateur")
    public UserResponse changeStatus(@PathVariable UUID id,
                                     @Valid @RequestBody UpdateUserStatusRequest request) {
        return userService.changeStatus(id, request.enabled());
    }
}
