package com.logistics.auth.bootstrap;

import com.logistics.auth.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Removes refresh tokens whose expiry has passed.
 *
 * <p>Without it the table grows without bound: every login adds a row that nothing ever deletes.
 * Expired rows are useless by definition, so this is pure housekeeping, not a security control.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private final RefreshTokenService refreshTokenService;

    @Scheduled(cron = "${logistics.cleanup.refresh-tokens-cron:0 0 3 * * *}")
    public void purgeExpiredTokens() {
        refreshTokenService.purgeExpired();
    }
}
