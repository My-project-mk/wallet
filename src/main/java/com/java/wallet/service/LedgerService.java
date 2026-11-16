package com.java.wallet.service;

import com.java.wallet.model.datatype.TransferType;
import com.java.wallet.model.entity.Transfer;
import com.java.wallet.model.entity.Wallet;

import java.math.BigDecimal;
import java.util.UUID;

public interface LedgerService {

    Transfer post(TransferType type, Wallet source, Wallet destination, BigDecimal amount, UUID idempotencyKey);
}
