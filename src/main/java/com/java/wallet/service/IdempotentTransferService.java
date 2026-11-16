package com.java.wallet.service;

import com.java.wallet.model.dto.TransferResult;
import com.java.wallet.model.requests.TransferRequest;

public interface IdempotentTransferService {

    TransferResult execute(TransferRequest transferRequest);
}
