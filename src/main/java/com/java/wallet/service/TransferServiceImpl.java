package com.java.wallet.service;

import com.java.wallet.model.datatype.TransferType;
import com.java.wallet.model.dto.TransferDto;
import com.java.wallet.model.entity.Transfer;
import com.java.wallet.model.entity.Wallet;
import com.java.wallet.model.requests.TransferRequest;
import com.java.wallet.repository.TransferRepository;
import com.java.wallet.repository.WalletRepository;
import com.java.wallet.utils.converters.DtoConverter;
import com.java.wallet.utils.helpers.exceptions.BadRequestRestApiException;
import com.java.wallet.utils.helpers.exceptions.ResourceNotFoundRestApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    private static final int TIMEOUT_SECONDS = 10;

    private final WalletRepository walletRepository;
    private final TransferRepository transferRepository;
    private final LedgerService ledgerService;
    private final IdempotencyService idempotencyService;

//   Executes a transfer for an already-claimed idempotency key, both wallets stay row-locked until commit
    @Override
    @Transactional(timeout = TIMEOUT_SECONDS)
    public TransferDto transfer(TransferRequest transferRequest) {

        Wallet[] wallets = lockWallets(transferRequest.getSourceWalletId(), transferRequest.getDestinationWalletId());
        Wallet source = wallets[0];
        Wallet destination = wallets[1];
        validateCurrency(source, transferRequest.getCurrency());
        validateCurrency(destination, transferRequest.getCurrency());
        Transfer transfer = ledgerService.post(TransferType.TRANSFER, source, destination, transferRequest.getAmount(), transferRequest.getIdempotencyKey());
        idempotencyService.complete(transferRequest.getIdempotencyKey());

        return DtoConverter.toTransferDto(transfer);
    }

    @Override
    @Transactional(readOnly = true)
    public TransferDto getTransferByIdempotencyKey(UUID idempotencyKey) {
        return transferRepository.findByIdempotencyKey(idempotencyKey)
                .map(DtoConverter::toTransferDto)
                .orElseThrow(() -> new ResourceNotFoundRestApiException()
                        .developerMessage("No transfer found for idempotency key %s", idempotencyKey)
                        .userMessage("Sorry. Transfer not found"));
    }

//    Locks both wallets, always in ascending public id order, so A->B and B->A
    private Wallet[] lockWallets(UUID sourcePublicId, UUID destinationPublicId) {
        boolean sourceFirst = sourcePublicId.compareTo(destinationPublicId) < 0;
        Wallet first = lockCustomerWallet(sourceFirst ? sourcePublicId : destinationPublicId);
        Wallet second = lockCustomerWallet(sourceFirst ? destinationPublicId : sourcePublicId);
        return sourceFirst ? new Wallet[]{first, second} : new Wallet[]{second, first};
    }

//    Treasury accounts are internal
    private Wallet lockCustomerWallet(UUID walletPublicId) {
        return walletRepository.findByPublicIdForUpdate(walletPublicId)
                .filter(wallet -> !wallet.isSystemAccount())
                .orElseThrow(() -> new ResourceNotFoundRestApiException()
                        .developerMessage("Wallet %s not found", walletPublicId)
                        .userMessage("Sorry. Wallet not found"));
    }

    private void validateCurrency(Wallet wallet, String currency) {
        if (!wallet.getCurrency().equals(currency)) {
            throw new BadRequestRestApiException()
                    .developerMessage("Wallet %s does not hold %s", wallet.getPublicId(), currency)
                    .userMessage("Sorry. Currency does not match the wallet currency");
        }
    }
}
