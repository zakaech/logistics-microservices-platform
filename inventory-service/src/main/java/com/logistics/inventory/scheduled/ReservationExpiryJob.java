package com.logistics.inventory.scheduled;

import com.logistics.inventory.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Releases stock held by reservations whose TTL has passed.
 *
 * <p>This is the last line of defence in the order saga. If order-service crashes between reserving
 * and confirming, nothing else would ever give those units back, and the stock would be
 * permanently invisible to every future order while being physically present on the shelf.
 *
 * <p>Runs every minute by default. The interval only bounds how long stranded stock stays stranded,
 * so it is a comfort setting, not a correctness one.
 */
@Component
@RequiredArgsConstructor
public class ReservationExpiryJob {

    private final ReservationService reservationService;

    @Scheduled(cron = "${logistics.reservation.expiry-sweep-cron:0 */1 * * * *}")
    public void releaseExpiredReservations() {
        reservationService.expireOverdueReservations();
    }
}
