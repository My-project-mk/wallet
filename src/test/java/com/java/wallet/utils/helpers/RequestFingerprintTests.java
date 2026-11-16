package com.java.wallet.utils.helpers;

import com.java.wallet.model.requests.TransferRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestFingerprintTests {

    private final UUID source = UUID.randomUUID();
    private final UUID destination = UUID.randomUUID();

    @Test
    public void sameAmountWrittenDifferentlyHasSameFingerprint() {
        assertThat(RequestFingerprint.of(request("50", "KES")))
                .isEqualTo(RequestFingerprint.of(request("50.00", "KES")));
    }

    @Test
    public void differentAmountHasDifferentFingerprint() {
        assertThat(RequestFingerprint.of(request("50.00", "KES")))
                .isNotEqualTo(RequestFingerprint.of(request("50.01", "KES")));
    }

    @Test
    public void differentCurrencyHasDifferentFingerprint() {
        assertThat(RequestFingerprint.of(request("50.00", "KES")))
                .isNotEqualTo(RequestFingerprint.of(request("50.00", "USD")));
    }

    private TransferRequest request(String amount, String currency) {
        TransferRequest transferRequest = new TransferRequest();
        transferRequest.setIdempotencyKey(UUID.randomUUID());
        transferRequest.setSourceWalletId(source);
        transferRequest.setDestinationWalletId(destination);
        transferRequest.setAmount(new BigDecimal(amount));
        transferRequest.setCurrency(currency);
        return transferRequest;
    }
}
