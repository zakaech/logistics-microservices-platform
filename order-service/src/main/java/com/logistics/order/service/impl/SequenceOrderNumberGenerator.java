package com.logistics.order.service.impl;

import com.logistics.order.service.OrderNumberGenerator;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZoneOffset;

/**
 * Order numbers from a database sequence.
 *
 * <p>A sequence rather than a count of existing rows: counting is not concurrency-safe, and it
 * would reuse a number after a deletion. Sequences also hand out values outside the transaction,
 * so two orders being placed at once never collide.
 *
 * <p>The year is part of the number because that is what operations people expect to read, not
 * because it carries meaning for the system - uniqueness comes from the sequence alone.
 */
@Component
@RequiredArgsConstructor
public class SequenceOrderNumberGenerator implements OrderNumberGenerator {

    private final EntityManager entityManager;
    private final Clock clock;

    @Override
    public String next() {
        Number value = (Number) entityManager
                .createNativeQuery("SELECT nextval('orders.order_number_seq')")
                .getSingleResult();

        int year = clock.instant().atZone(ZoneOffset.UTC).getYear();
        return String.format("ORD-%d-%06d", year, value.longValue());
    }
}
