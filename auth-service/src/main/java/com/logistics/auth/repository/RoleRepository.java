package com.logistics.auth.repository;

import com.logistics.auth.domain.entity.Role;
import com.logistics.auth.domain.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(RoleName name);

    Set<Role> findByNameIn(Collection<RoleName> names);
}
