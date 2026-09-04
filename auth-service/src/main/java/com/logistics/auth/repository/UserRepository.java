package com.logistics.auth.repository;

import com.logistics.auth.domain.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Case-insensitive search on email, first name and last name for the administration screen.
     *
     * <p>The term is mandatory. An earlier version accepted null and started with
     * {@code WHERE :term IS NULL OR ...}, which fails on PostgreSQL: an untyped null parameter is
     * bound as {@code bytea} and {@code lower(bytea)} does not exist. Rather than papering over it
     * with a CAST, the service asks two different questions - "every user" uses the inherited
     * {@code findAll(Pageable)}, "users matching a term" uses this query.
     */
    @Query("""
            SELECT u FROM User u
            WHERE LOWER(u.email)     LIKE LOWER(CONCAT('%', :term, '%'))
               OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :term, '%'))
               OR LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :term, '%'))
            """)
    Page<User> search(@Param("term") String term, Pageable pageable);
}
