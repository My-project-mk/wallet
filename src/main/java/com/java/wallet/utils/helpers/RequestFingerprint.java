package com.java.wallet.utils.helpers;

import com.java.wallet.model.requests.CreateWalletRequest;
import com.java.wallet.model.requests.TransferRequest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;


//SHA-256 over the canonical form of a request - used to detect an idempotency key being reused with a different payload
public final class RequestFingerprint {

    private RequestFingerprint() {
    }

    public static String of(TransferRequest request) {
        return sha256(request.getSourceWalletId() + "|" + request.getDestinationWalletId() + "|"
                + Money.of(request.getAmount()).toPlainString() + "|" + request.getCurrency());
    }

//    No opening balance and "0.00" are the same request. The "wallet|" prefix keeps it distinct from any transfer
    public static String of(CreateWalletRequest request) {
        BigDecimal initialBalance = request.getInitialBalance() == null ? Money.zero() : Money.of(request.getInitialBalance());
        return sha256("wallet|" + request.getCurrency() + "|" + initialBalance.toPlainString());
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
