package com.java.wallet.utils.converters;

import com.java.wallet.model.dto.LedgerEntryDto;
import com.java.wallet.model.dto.TransferDto;
import com.java.wallet.model.dto.WalletBalanceDto;
import com.java.wallet.model.dto.WalletDto;
import com.java.wallet.model.entity.LedgerEntry;
import com.java.wallet.model.entity.Transfer;
import com.java.wallet.model.entity.Wallet;

import java.util.List;

public final class DtoConverter {

    private DtoConverter() {
    }

    public static WalletDto toWalletDto(Wallet wallet) {
        WalletDto walletDto = new WalletDto();
        walletDto.setWalletId(wallet.getPublicId());
        walletDto.setCurrency(wallet.getCurrency());
        walletDto.setBalance(wallet.getBalance());
        walletDto.setCreatedDate(wallet.getCreatedDate());
        return walletDto;
    }

    public static WalletBalanceDto toWalletBalanceDto(Wallet wallet, List<LedgerEntry> recentEntries) {
        WalletBalanceDto walletBalanceDto = new WalletBalanceDto();
        walletBalanceDto.setWalletId(wallet.getPublicId());
        walletBalanceDto.setCurrency(wallet.getCurrency());
        walletBalanceDto.setAvailableBalance(wallet.getBalance());
        walletBalanceDto.setRecentEntries(recentEntries.stream().map(DtoConverter::toLedgerEntryDto).toList());
        return walletBalanceDto;
    }

    public static LedgerEntryDto toLedgerEntryDto(LedgerEntry ledgerEntry) {
        LedgerEntryDto ledgerEntryDto = new LedgerEntryDto();
        ledgerEntryDto.setTransferId(ledgerEntry.getTransfer().getPublicId());
        ledgerEntryDto.setEntryType(ledgerEntry.getEntryType());
        ledgerEntryDto.setAmount(ledgerEntry.getAmount());
        ledgerEntryDto.setBalanceAfter(ledgerEntry.getBalanceAfter());
        ledgerEntryDto.setCreatedDate(ledgerEntry.getCreatedDate());
        return ledgerEntryDto;
    }

    public static TransferDto toTransferDto(Transfer transfer) {
        TransferDto transferDto = new TransferDto();
        transferDto.setTransferId(transfer.getPublicId());
        transferDto.setIdempotencyKey(transfer.getIdempotencyKey());
        transferDto.setSourceWalletId(transfer.getSourceWallet().getPublicId());
        transferDto.setDestinationWalletId(transfer.getDestinationWallet().getPublicId());
        transferDto.setAmount(transfer.getAmount());
        transferDto.setCurrency(transfer.getCurrency());
        transferDto.setStatus(TransferDto.STATUS_COMPLETED);
        transferDto.setCreatedDate(transfer.getCreatedDate());
        return transferDto;
    }
}
