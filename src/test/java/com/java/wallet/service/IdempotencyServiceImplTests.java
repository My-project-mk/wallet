package com.java.wallet.service;

import com.java.wallet.WalletApplicationTests;
import com.java.wallet.model.datatype.ClaimOutcome;
import com.java.wallet.utils.helpers.exceptions.ConflictRestApiException;
import com.java.wallet.utils.helpers.exceptions.TooEarlyRestApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class IdempotencyServiceImplTests extends WalletApplicationTests {

    private static final String REQUEST_HASH = "a".repeat(64);
    private static final String OTHER_REQUEST_HASH = "b".repeat(64);

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final UUID idempotencyKey = UUID.randomUUID();

    @Test
    public void claimWorks() {
        assertThat(idempotencyService.claim(idempotencyKey, REQUEST_HASH)).isEqualTo(ClaimOutcome.CLAIMED);
    }

    @Test
    public void claimFailsWhileInProgress() {
        idempotencyService.claim(idempotencyKey, REQUEST_HASH);

        assertThatThrownBy(() -> idempotencyService.claim(idempotencyKey, REQUEST_HASH))
                .isInstanceOf(TooEarlyRestApiException.class);
    }

    @Test
    public void claimFailsDifferentPayload() {
        idempotencyService.claim(idempotencyKey, REQUEST_HASH);

        assertThatThrownBy(() -> idempotencyService.claim(idempotencyKey, OTHER_REQUEST_HASH))
                .isInstanceOf(ConflictRestApiException.class);
    }

    @Test
    public void claimFailsDifferentPayloadAfterCompletion() {
        idempotencyService.claim(idempotencyKey, REQUEST_HASH);
        complete(idempotencyKey);

        assertThatThrownBy(() -> idempotencyService.claim(idempotencyKey, OTHER_REQUEST_HASH))
                .isInstanceOf(ConflictRestApiException.class);
    }

    @Test
    public void claimOfCompletedKeyReturnsAlreadyCompleted() {
        idempotencyService.claim(idempotencyKey, REQUEST_HASH);
        complete(idempotencyKey);

        assertThat(idempotencyService.claim(idempotencyKey, REQUEST_HASH)).isEqualTo(ClaimOutcome.ALREADY_COMPLETED);
    }

    @Test
    public void claimOfReleasedKeyWorks() {
        idempotencyService.claim(idempotencyKey, REQUEST_HASH);
        idempotencyService.release(idempotencyKey);

        assertThat(idempotencyService.claim(idempotencyKey, REQUEST_HASH)).isEqualTo(ClaimOutcome.CLAIMED);
    }

    @Test
    public void claimOfAbandonedKeyWorksAfterLeaseExpires() {
        idempotencyService.claim(idempotencyKey, REQUEST_HASH);
        jdbcTemplate.update("UPDATE idempotency_keys SET locked_at = now() - interval '1 hour' WHERE idempotency_key = ?",
                idempotencyKey);

        assertThat(idempotencyService.claim(idempotencyKey, REQUEST_HASH)).isEqualTo(ClaimOutcome.CLAIMED);
        assertThatThrownBy(() -> idempotencyService.claim(idempotencyKey, REQUEST_HASH))
                .as("the reclaim renewed the lease")
                .isInstanceOf(TooEarlyRestApiException.class);
    }

    private void complete(UUID key) {
        transactionTemplate.executeWithoutResult(status -> idempotencyService.complete(key));
    }
}
