package com.java.wallet.service;

import com.java.wallet.model.dto.WalletBalanceDto;
import com.java.wallet.model.dto.WalletDto;
import com.java.wallet.model.requests.CreateWalletRequest;

import java.util.UUID;

public interface WalletService {

    WalletDto createWallet(CreateWalletRequest createWalletRequest);

    WalletBalanceDto getWalletBalance(UUID walletPublicId, int limit);
}
