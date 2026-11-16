package com.java.wallet.web;

import com.java.wallet.model.dto.WalletBalanceDto;
import com.java.wallet.model.dto.WalletDto;
import com.java.wallet.model.entity.Wallet;
import com.java.wallet.model.requests.CreateWalletRequest;
import com.java.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/wallets")
@Tag(name = "wallets")
public class WalletRestController {

    private final WalletService walletService;

    public WalletRestController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    @Operation(summary = "Create Wallet", description = "Opens a wallet, optionally funded with an opening balance")
    public ResponseEntity<WalletDto> createWallet(@RequestBody @Valid CreateWalletRequest createWalletRequest) {
        WalletDto walletDto = walletService.createWallet(createWalletRequest);

        return new ResponseEntity<>(walletDto, new HttpHeaders(), HttpStatus.CREATED);
    }

    @GetMapping("/{publicId}/balance")
    @Operation(summary = "Get Wallet Balance", description = "Available balance and the most recent ledger entries")
    public WalletBalanceDto getWalletBalance(@PathVariable("publicId") UUID publicId,
                                             @RequestParam(name = "limit", defaultValue = "20") @Min(1) @Max(100) int limit) {

        return walletService.getWalletBalance(publicId, limit);
    }
}
