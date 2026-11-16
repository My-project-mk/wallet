package com.java.wallet.web;

import com.java.wallet.model.dto.TransferDto;
import com.java.wallet.model.dto.TransferResult;
import com.java.wallet.model.requests.TransferRequest;
import com.java.wallet.service.IdempotentTransferService;
import com.java.wallet.utils.helpers.ApiHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/transfers")
@Tag(name = "transfers")
public class TransferRestController {

    public static final String REPLAYED_HEADER = ApiHeaders.IDEMPOTENT_REPLAYED;

    private final IdempotentTransferService idempotentTransferService;

    public TransferRestController(IdempotentTransferService idempotentTransferService) {
        this.idempotentTransferService = idempotentTransferService;
    }

    @PostMapping
    @Operation(summary = "Create Transfer", description = "Idempotent wallet-to-wallet transfer. A replay of a "
            + "completed request returns the original response with the Idempotent-Replayed header set to true")
    public ResponseEntity<TransferDto> createTransfer(@RequestBody @Valid TransferRequest transferRequest) {
        TransferResult transferResult = idempotentTransferService.execute(transferRequest);
        HttpHeaders headers = new HttpHeaders();
        headers.add(REPLAYED_HEADER, String.valueOf(transferResult.isReplayed()));

        return new ResponseEntity<>(transferResult.getTransfer(), headers, HttpStatus.CREATED);
    }
}
