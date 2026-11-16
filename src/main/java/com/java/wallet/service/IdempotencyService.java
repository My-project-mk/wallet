package com.java.wallet.service;

import com.java.wallet.model.datatype.ClaimOutcome;

import java.util.UUID;

public interface IdempotencyService {

    ClaimOutcome claim(UUID idempotencyKey, String requestHash);

    void complete(UUID idempotencyKey);

    void release(UUID idempotencyKey);
}
