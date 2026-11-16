package com.java.wallet.service;

import com.java.wallet.model.datatype.ClaimOutcome;
import com.java.wallet.model.datatype.IdempotencyStatus;
import com.java.wallet.model.entity.IdempotencyRecord;
import com.java.wallet.repository.IdempotencyRepository;
import com.java.wallet.utils.helpers.exceptions.ConflictRestApiException;
import com.java.wallet.utils.helpers.exceptions.TooEarlyRestApiException;
import com.java.wallet.utils.helpers.exceptions.web.rest.RestApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class IdempotencyServiceImpl implements IdempotencyService {

    private final IdempotencyRepository idempotencyRepository;
    private final Duration leaseDuration;

    public IdempotencyServiceImpl(IdempotencyRepository idempotencyRepository, @Value("${wallet.idempotency.lease-duration}") Duration leaseDuration) {
        this.idempotencyRepository = idempotencyRepository;
        this.leaseDuration = leaseDuration;
    }

    @Override
    @Transactional
    public ClaimOutcome claim(UUID idempotencyKey, String requestHash) {
        LocalDateTime now = LocalDateTime.now();

        if (idempotencyRepository.insertIfAbsent(idempotencyKey, requestHash, now) == 1) {
            return ClaimOutcome.CLAIMED;
        }

        IdempotencyRecord existing = idempotencyRepository.findByIdempotencyKeyForUpdate(idempotencyKey).orElseThrow(() -> requestInProgress(idempotencyKey));
        return resolveExisting(existing, requestHash, now);
    }

//    Runs inside the transfer transaction, so the transfer and the completed key commit or roll back together
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(UUID idempotencyKey) {
        IdempotencyRecord idempotencyRecord = idempotencyRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Idempotency key " + idempotencyKey + " was not claimed"));

        idempotencyRecord.setStatus(IdempotencyStatus.COMPLETED);
    }

//    Frees the key after a failed attempt so the client can safely retry
    @Override
    @Transactional
    public void release(UUID idempotencyKey) {
        idempotencyRepository.deleteByIdempotencyKey(idempotencyKey);
    }

    private ClaimOutcome resolveExisting(IdempotencyRecord existing, String requestHash, LocalDateTime now) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new ConflictRestApiException()
                    .developerMessage("Idempotency key %s was already used with a different payload", existing.getIdempotencyKey())
                    .userMessage("Sorry. This idempotency key was already used for a different request");
        }

        if (existing.getStatus() == IdempotencyStatus.COMPLETED) {
            return ClaimOutcome.ALREADY_COMPLETED;
        }

        if (isLeaseExpired(existing, now)) {
            log.warn("Reclaiming abandoned idempotency key {}", existing.getIdempotencyKey());
            existing.setLockedAt(now);
            return ClaimOutcome.CLAIMED;
        }
        throw requestInProgress(existing.getIdempotencyKey());
    }

    private boolean isLeaseExpired(IdempotencyRecord idempotencyRecord, LocalDateTime now) {
        return idempotencyRecord.getLockedAt().plus(leaseDuration).isBefore(now);
    }

    private RestApiException requestInProgress(UUID idempotencyKey) {
        return new TooEarlyRestApiException()
                .developerMessage("Request with idempotency key %s is still being processed", idempotencyKey)
                .userMessage("Your request is still being processed. Please retry shortly");
    }
}
