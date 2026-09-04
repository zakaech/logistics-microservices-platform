package com.logistics.inventory.integration;

import com.logistics.inventory.domain.entity.StockItem;
import com.logistics.inventory.domain.entity.Warehouse;
import com.logistics.inventory.domain.enums.ReservationStatus;
import com.logistics.inventory.domain.vo.Address;
import com.logistics.inventory.domain.vo.GeoPoint;
import com.logistics.inventory.dto.request.CreateReservationRequest;
import com.logistics.inventory.exception.InsufficientStockException;
import com.logistics.inventory.repository.ReservationRepository;
import com.logistics.inventory.repository.StockItemRepository;
import com.logistics.inventory.repository.WarehouseRepository;
import com.logistics.inventory.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the anti-oversell guarantee under real concurrency.
 *
 * <p>Against a real PostgreSQL container, not H2: row-locking semantics are exactly what is under
 * test, and H2 does not reproduce them. This is the test that justifies the pessimistic strategy.
 */
// disabledWithoutDocker: the class is SKIPPED, not failed, where no usable Docker is reachable.
// A build that cannot start a container should report "not verified here", never a false failure -
// and never a false pass either, which is why the test is not simply deleted.
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "security.jwt.jwk-set-uri=http://localhost:0/jwks.json",
        "security.jwt.issuer=logistics-auth-test",
        "logistics.reservation.default-ttl=PT5M"
})
class ReservationConcurrencyIT {

    private static final String PRODUCT = "product-under-contention";
    private static final int UNITS_IN_STOCK = 10;
    private static final int COMPETING_ORDERS = 40;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ReservationService reservationService;
    @Autowired
    private WarehouseRepository warehouseRepository;
    @Autowired
    private StockItemRepository stockItemRepository;
    @Autowired
    private ReservationRepository reservationRepository;

    private UUID warehouseId;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        stockItemRepository.deleteAll();
        warehouseRepository.deleteAll();

        Warehouse warehouse = warehouseRepository.save(Warehouse.builder()
                .code("WH-TEST-01")
                .name("Test hub")
                .location(new GeoPoint(new BigDecimal("33.573100"), new BigDecimal("-7.589800")))
                .address(new Address("Zone industrielle", "Casablanca", "20600", "MA"))
                .active(true)
                .build());
        warehouseId = warehouse.getId();

        stockItemRepository.save(StockItem.builder()
                .warehouse(warehouse)
                .productId(PRODUCT)
                .quantityOnHand(UNITS_IN_STOCK)
                .quantityReserved(0)
                .reorderThreshold(0)
                .updatedAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("40 orders racing for 10 units: exactly 10 succeed and nothing is oversold")
    void concurrentReservationsNeverOversell() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(COMPETING_ORDERS);

        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejectedForStock = new AtomicInteger();
        AtomicInteger otherFailures = new AtomicInteger();

        for (int i = 0; i < COMPETING_ORDERS; i++) {
            String reference = "ORD-CONCURRENT-" + i;
            pool.submit(() -> {
                try {
                    // Every thread waits on the same gate, so they hit the rows together rather
                    // than politely queueing behind each other.
                    startGate.await();
                    reservationService.reserve(oneUnitFor(reference));
                    succeeded.incrementAndGet();
                } catch (InsufficientStockException e) {
                    rejectedForStock.incrementAndGet();
                } catch (Exception e) {
                    otherFailures.incrementAndGet();
                } finally {
                    finished.countDown();
                }
            });
        }

        startGate.countDown();
        assertThat(finished.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        StockItem finalState = stockItemRepository
                .findByWarehouseIdAndProductId(warehouseId, PRODUCT).orElseThrow();

        // The guarantee: never more reserved than physically held.
        assertThat(finalState.getQuantityReserved())
                .isLessThanOrEqualTo(finalState.getQuantityOnHand());
        // The precise outcome: the stock is fully consumed and not one unit more.
        assertThat(finalState.getQuantityReserved()).isEqualTo(UNITS_IN_STOCK);
        assertThat(finalState.availableQuantity()).isZero();

        assertThat(succeeded.get()).isEqualTo(UNITS_IN_STOCK);
        assertThat(rejectedForStock.get()).isEqualTo(COMPETING_ORDERS - UNITS_IN_STOCK);
        // No deadlock, no lock timeout, no version conflict: with the ordered pessimistic lock a
        // loser loses cleanly, on stock, rather than on an infrastructure error.
        assertThat(otherFailures.get()).isZero();

        assertThat(reservationRepository.findAll())
                .hasSize(UNITS_IN_STOCK)
                .allMatch(reservation -> reservation.getStatus() == ReservationStatus.ACTIVE);
    }

    @Test
    @DisplayName("replaying the same reference is idempotent, even from concurrent callers")
    void concurrentRetriesOfTheSameOrderReserveOnce() throws InterruptedException {
        int callers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(callers);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger returnedExisting = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        for (int i = 0; i < callers; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    var result = reservationService.reserve(oneUnitFor("ORD-RETRIED"));
                    if (result.created()) {
                        created.incrementAndGet();
                    } else {
                        returnedExisting.incrementAndGet();
                    }
                } catch (Exception e) {
                    // A genuine race on the unique reference surfaces as a constraint violation,
                    // which the API maps to 409. What must never happen is a second hold.
                    conflicts.incrementAndGet();
                } finally {
                    finished.countDown();
                }
            });
        }

        startGate.countDown();
        assertThat(finished.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(created.get()).isEqualTo(1);
        assertThat(created.get() + returnedExisting.get() + conflicts.get()).isEqualTo(callers);

        StockItem finalState = stockItemRepository
                .findByWarehouseIdAndProductId(warehouseId, PRODUCT).orElseThrow();
        assertThat(finalState.getQuantityReserved()).isEqualTo(1);
        assertThat(reservationRepository.findAll()).hasSize(1);
    }

    private CreateReservationRequest oneUnitFor(String reference) {
        return new CreateReservationRequest(reference, 300, List.of(
                new CreateReservationRequest.Segment(warehouseId, List.of(
                        new CreateReservationRequest.Line(PRODUCT, 1)))));
    }
}
