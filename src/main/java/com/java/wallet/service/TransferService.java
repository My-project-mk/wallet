package com.java.wallet.service;

import com.java.wallet.model.dto.TransferDto;
import com.java.wallet.model.requests.TransferRequest;

import java.util.UUID;

public interface TransferService {

    TransferDto transfer(TransferRequest transferRequest);

    TransferDto getTransferByIdempotencyKey(UUID idempotencyKey);
}
