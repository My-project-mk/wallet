package com.java.wallet.service;

import com.java.wallet.model.datatype.ClaimOutcome;
import com.java.wallet.model.dto.WalletDto;
import com.java.wallet.model.dto.WalletResult;
import com.java.wallet.model.requests.CreateWalletRequest;
import com.java.wallet.utils.helpers.RequestFingerprint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

//Guarantees each idempotency key opens at most one wallet, so a retried create never funds a second wallet.
//Deliberately not transactional itself: the key claim must commit before the wallet is created so concurrent duplicates can see it
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotentWalletServiceImpl implements IdempotentWalletService {

    private final IdempotencyService idempotencyService;
    private final WalletService walletService;

    @Override
    public WalletResult execute(CreateWalletRequest createWalletRequest) {
        ClaimOutcome claimOutcome = idempotencyService.claim(createWalletRequest.getIdempotencyKey(),
                RequestFingerprint.of(createWalletRequest));

        if (claimOutcome == ClaimOutcome.ALREADY_COMPLETED) {
            log.info("Replaying wallet creation for idempotency key {}", createWalletRequest.getIdempotencyKey());
            return new WalletResult(walletService.getWalletByIdempotencyKey(createWalletRequest.getIdempotencyKey()), true);
        }

        return new WalletResult(processClaimed(createWalletRequest), false);
    }

    private WalletDto processClaimed(CreateWalletRequest createWalletRequest) {
        try {
            return walletService.createWallet(createWalletRequest);
        } catch (RuntimeException e) {
            idempotencyService.release(createWalletRequest.getIdempotencyKey());
            throw e;
        }
    }
}
