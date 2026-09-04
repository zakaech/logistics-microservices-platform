package com.logistics.inventory.repository;

import com.logistics.inventory.domain.entity.Reservation;
import com.logistics.inventory.domain.enums.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    /** Lookup by order number. The unique constraint on it is what makes reserving idempotent. */
    Optional<Reservation> findByReference(String reference);

    @Query("SELECT r FROM Reservation r LEFT JOIN FETCH r.lines WHERE r.id = :id")
    Optional<Reservation> findByIdWithLines(@Param("id") UUID id);

    @Query("SELECT r FROM Reservation r LEFT JOIN FETCH r.lines WHERE r.reference = :reference")
    Optional<Reservation> findByReferenceWithLines(@Param("reference") String reference);

    /** Feeds the sweeper that releases holds left behind by a crashed orchestrator. */
    @Query("SELECT r.id FROM Reservation r WHERE r.status = :status AND r.expiresAt < :now")
    List<UUID> findIdsByStatusAndExpiresAtBefore(@Param("status") ReservationStatus status,
                                                 @Param("now") Instant now);
}
