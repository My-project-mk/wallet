package com.java.wallet.service;

import com.java.wallet.model.datatype.TransferType;
import com.java.wallet.model.dto.WalletBalanceDto;
import com.java.wallet.model.dto.WalletDto;
import com.java.wallet.model.entity.LedgerEntry;
import com.java.wallet.model.entity.Wallet;
import com.java.wallet.model.requests.CreateWalletRequest;
import com.java.wallet.repository.LedgerEntryRepository;
import com.java.wallet.repository.WalletRepository;
import com.java.wallet.utils.converters.DtoConverter;
import com.java.wallet.utils.helpers.Money;
import com.java.wallet.utils.helpers.exceptions.BadRequestRestApiException;
import com.java.wallet.utils.helpers.exceptions.ResourceNotFoundRestApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

//    Kept below the idempotency lease, so an abandoned key is only reclaimed once its transaction is dead
    private static final int TIMEOUT_SECONDS = 10;

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerService ledgerService;
    private final IdempotencyService idempotencyService;

//    Runs for an already-claimed idempotency key. A non-zero opening balance is posted as a double-entry deposit
//    from the currency's treasury account. The wallet and its completed key commit together
    @Override
    @Transactional(timeout = TIMEOUT_SECONDS)
    public WalletDto createWallet(CreateWalletRequest createWalletRequest) {
        Wallet treasury = lockTreasury(createWalletRequest.getCurrency());
        Wallet wallet = walletRepository.save(buildWallet(createWalletRequest));

        if (hasOpeningBalance(createWalletRequest)) {
            ledgerService.post(TransferType.FUNDING, treasury, wallet, createWalletRequest.getInitialBalance(), null);
        }

        idempotencyService.complete(createWalletRequest.getIdempotencyKey());
        log.info("Created {} wallet {}", wallet.getCurrency(), wallet.getPublicId());
        return DtoConverter.toWalletDto(wallet);
    }

    @Override
    @Transactional(readOnly = true)
    public WalletDto getWalletByIdempotencyKey(UUID idempotencyKey) {
        return walletRepository.findByIdempotencyKey(idempotencyKey)
                .map(DtoConverter::toWalletDto)
                .orElseThrow(() -> new ResourceNotFoundRestApiException()
                        .developerMessage("No wallet found for idempotency key %s", idempotencyKey)
                        .userMessage("Sorry. Wallet not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public WalletBalanceDto getWalletBalance(UUID walletPublicId, int limit) {
        Wallet wallet = walletRepository.findByPublicId(walletPublicId).orElseThrow(() -> new ResourceNotFoundRestApiException()
                    .developerMessage("Wallet %s not found", walletPublicId)
                    .userMessage("Sorry. Wallet not found"));

        List<LedgerEntry> recentEntries = ledgerEntryRepository.findByWalletOrderByIdDesc(wallet, PageRequest.of(0, limit));
        return DtoConverter.toWalletBalanceDto(wallet, recentEntries);
    }

    private Wallet lockTreasury(String currency) {
        return walletRepository.findTreasuryForUpdate(currency).orElseThrow(() -> new BadRequestRestApiException()
                .developerMessage("Currency %s is not supported", currency)
                .userMessage("Sorry. Currency not supported"));
    }

    private Wallet buildWallet(CreateWalletRequest createWalletRequest) {
        Wallet wallet = new Wallet();
        wallet.setCurrency(createWalletRequest.getCurrency());
        wallet.setBalance(Money.zero());
        wallet.setSystemAccount(false);
        wallet.setIdempotencyKey(createWalletRequest.getIdempotencyKey());
        return wallet;
    }

    private boolean hasOpeningBalance(CreateWalletRequest createWalletRequest) {
        BigDecimal initialBalance = createWalletRequest.getInitialBalance();
        return initialBalance != null && initialBalance.signum() > 0;
    }
}
