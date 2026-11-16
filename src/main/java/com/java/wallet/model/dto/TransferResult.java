package com.java.wallet.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TransferResult {

    private final TransferDto transfer;
    private final boolean replayed;
}
