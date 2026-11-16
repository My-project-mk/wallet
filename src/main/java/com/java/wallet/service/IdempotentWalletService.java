package com.java.wallet.service;

import com.java.wallet.model.dto.WalletResult;
import com.java.wallet.model.requests.CreateWalletRequest;

public interface IdempotentWalletService {

    WalletResult execute(CreateWalletRequest createWalletRequest);
}
