package com.java.wallet.web;

import com.java.wallet.WalletApplicationTests;
import com.java.wallet.model.requests.TransferRequest;
import com.java.wallet.service.IdempotencyService;
import com.java.wallet.utils.helpers.RequestFingerprint;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class TransferIdempotencyRestControllerTests extends WalletApplicationTests {

    @Autowired
    private IdempotencyService idempotencyService;

    private UUID source;
    private UUID destination;

    @BeforeEach
    public void initSetup() {
        source = createWallet("KES", "100.00");
        destination = createWallet("KES", null);
    }

    @Test
    public void replayReturnsOriginalResponseWithoutReprocessing() {
        TransferRequest transferRequest = transferRequest(source, destination, "30.00");
        Response original = postTransfer(transferRequest);

        postTransfer(transferRequest)
                .then()
                .statusCode(201)
                .header(TransferRestController.REPLAYED_HEADER, "true")
                .body("transfer_id", equalTo(original.path("transfer_id")))
                .body("create_date", equalTo(original.path("create_date")));

        assertThat(getBalance(source)).as("debited once").isEqualByComparingTo("70.00");
        assertThat(countTransfers(transferRequest.getIdempotencyKey())).isEqualTo(1);
    }

    @Test
    public void replayMatchesAmountRegardlessOfFormatting() {
        TransferRequest transferRequest = transferRequest(source, destination, "30");
        postTransfer(transferRequest).then().statusCode(201);
        transferRequest.setAmount(new BigDecimal("30.00"));

        postTransfer(transferRequest)
                .then()
                .statusCode(201)
                .header(TransferRestController.REPLAYED_HEADER, "true");
    }

    @Test
    public void replayFailsDifferentPayload() {
        TransferRequest transferRequest = transferRequest(source, destination, "30.00");
        postTransfer(transferRequest).then().statusCode(201);
        transferRequest.setAmount(new BigDecimal("31.00"));

        postTransfer(transferRequest)
                .then()
                .statusCode(409)
                .body("user_message", equalTo("Sorry. This idempotency key was already used for a different request"));

        assertThat(getBalance(source)).isEqualByComparingTo("70.00");
    }

    @Test
    public void replayFailsWhileOriginalStillProcessing() {
        TransferRequest transferRequest = transferRequest(source, destination, "30.00");
        idempotencyService.claim(transferRequest.getIdempotencyKey(), RequestFingerprint.of(transferRequest));

        postTransfer(transferRequest).then().statusCode(425);

        assertThat(getBalance(source)).isEqualByComparingTo("100.00");
        idempotencyService.release(transferRequest.getIdempotencyKey());
    }

    private int countTransfers(UUID idempotencyKey) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transfers WHERE idempotency_key = ?",
                Integer.class, idempotencyKey);
    }
}
