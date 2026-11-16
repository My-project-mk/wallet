package com.java.wallet.service;

import com.java.wallet.model.datatype.EntryType;
import com.java.wallet.model.datatype.TransferType;
import com.java.wallet.model.entity.LedgerEntry;
import com.java.wallet.model.entity.Transfer;
import com.java.wallet.model.entity.Wallet;
import com.java.wallet.repository.LedgerEntryRepository;
import com.java.wallet.repository.TransferRepository;
import com.java.wallet.utils.helpers.Money;
import com.java.wallet.utils.helpers.exceptions.BadRequestRestApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

// Money moves here. Every posting debits one wallet and credits another by the same amount and records
// both ledger entries, so the ledger always balances

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerServiceImpl implements LedgerService {

    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Transfer post(TransferType type, Wallet source, Wallet destination, BigDecimal amount, UUID idempotencyKey) {

        BigDecimal postedAmount = Money.of(amount);
        debit(source, postedAmount);
        credit(destination, postedAmount);
        Transfer transfer = transferRepository.save(buildTransfer(type, source, destination, postedAmount, idempotencyKey));
        ledgerEntryRepository.save(buildLedgerEntry(transfer, source, EntryType.DEBIT));
        ledgerEntryRepository.save(buildLedgerEntry(transfer, destination, EntryType.CREDIT));
        log.info("Posted {} {} {} from wallet {} to wallet {}", type, postedAmount, transfer.getCurrency(), source.getPublicId(), destination.getPublicId());

        return transfer;
    }

//    Balance guard - a customer wallet can never go below zero
    private void debit(Wallet wallet, BigDecimal amount) {

        if (!wallet.isSystemAccount() && wallet.getBalance().compareTo(amount) < 0) {
            throw new BadRequestRestApiException()
                    .developerMessage("Insufficient funds in wallet %s", wallet.getPublicId())
                    .userMessage("Sorry. Insufficient funds");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
    }

    private void credit(Wallet wallet, BigDecimal amount) {
        wallet.setBalance(wallet.getBalance().add(amount));
    }

    private Transfer buildTransfer(TransferType type, Wallet source, Wallet destination, BigDecimal amount, UUID idempotencyKey) {
        Transfer transfer = new Transfer();
        transfer.setType(type);
        transfer.setSourceWallet(source);
        transfer.setDestinationWallet(destination);
        transfer.setAmount(amount);
        transfer.setCurrency(source.getCurrency());
        transfer.setIdempotencyKey(idempotencyKey);
        return transfer;
    }

    private LedgerEntry buildLedgerEntry(Transfer transfer, Wallet wallet, EntryType entryType) {
        LedgerEntry ledgerEntry = new LedgerEntry();
        ledgerEntry.setTransfer(transfer);
        ledgerEntry.setWallet(wallet);
        ledgerEntry.setEntryType(entryType);
        ledgerEntry.setAmount(transfer.getAmount());
        ledgerEntry.setBalanceAfter(wallet.getBalance());
        return ledgerEntry;
    }
}
