package com.java.wallet.service;

import com.java.wallet.model.datatype.ClaimOutcome;
import com.java.wallet.model.dto.TransferDto;
import com.java.wallet.model.dto.TransferResult;
import com.java.wallet.model.requests.TransferRequest;
import com.java.wallet.utils.helpers.RequestFingerprint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


//Guarantees each idempotency key moves money at most once
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotentTransferServiceImpl implements IdempotentTransferService {

    private final IdempotencyService idempotencyService;
    private final TransferService transferService;

    @Override
    public TransferResult execute(TransferRequest transferRequest) {

        ClaimOutcome claimOutcome = idempotencyService.claim(transferRequest.getIdempotencyKey(), RequestFingerprint.of(transferRequest));

        if (claimOutcome == ClaimOutcome.ALREADY_COMPLETED) {
            log.info("Replaying transfer for idempotency key {}", transferRequest.getIdempotencyKey());
            return new TransferResult(transferService.getTransferByIdempotencyKey(transferRequest.getIdempotencyKey()), true);
        }
        return new TransferResult(processClaimed(transferRequest), false);
    }

    private TransferDto processClaimed(TransferRequest transferRequest) {
        try {
            return transferService.transfer(transferRequest);
        } catch (RuntimeException e) {
            idempotencyService.release(transferRequest.getIdempotencyKey());
            throw e;
        }
    }
}
