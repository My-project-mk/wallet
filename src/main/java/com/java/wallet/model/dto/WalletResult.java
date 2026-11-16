package com.java.wallet.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class WalletResult {

    private final WalletDto wallet;
    private final boolean replayed;
}
