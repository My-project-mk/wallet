package com.java.wallet.web;

import com.java.wallet.WalletApplicationTests;
import com.java.wallet.model.requests.CreateWalletRequest;
import com.java.wallet.utils.helpers.ApiHeaders;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

public class WalletIdempotencyRestControllerTests extends WalletApplicationTests {

    @Test
    public void replayReturnsOriginalWalletWithoutFundingTwice() {
        BigDecimal treasuryBefore = getBalance(KES_TREASURY_ID);
        CreateWalletRequest createWalletRequest = walletRequest("KES", "100.00");
        Response original = postWallet(createWalletRequest);

        postWallet(createWalletRequest)
                .then()
                .statusCode(201)
                .header(ApiHeaders.IDEMPOTENT_REPLAYED, "true")
                .body("wallet_id", equalTo(original.path("wallet_id")))
                .body("balance", equalTo("100.00"));

        assertThat(countWallets(createWalletRequest.getIdempotencyKey())).as("one wallet").isEqualTo(1);
        assertThat(getBalance(KES_TREASURY_ID)).as("treasury debited once")
                .isEqualByComparingTo(treasuryBefore.subtract(new BigDecimal("100.00")));
    }

    @Test
    public void firstCreationIsNotFlaggedAsReplay() {
        postWallet(walletRequest("KES", null))
                .then()
                .statusCode(201)
                .header(ApiHeaders.IDEMPOTENT_REPLAYED, "false");
    }

    @Test
    public void replayMatchesMissingAndZeroOpeningBalance() {
        CreateWalletRequest createWalletRequest = walletRequest("KES", null);
        postWallet(createWalletRequest).then().statusCode(201);
        createWalletRequest.setInitialBalance(new BigDecimal("0.00"));

        postWallet(createWalletRequest)
                .then()
                .statusCode(201)
                .header(ApiHeaders.IDEMPOTENT_REPLAYED, "true");
    }

    @Test
    public void replayFailsDifferentPayload() {
        CreateWalletRequest createWalletRequest = walletRequest("KES", "100.00");
        postWallet(createWalletRequest).then().statusCode(201);
        createWalletRequest.setInitialBalance(new BigDecimal("500.00"));

        postWallet(createWalletRequest)
                .then()
                .statusCode(409)
                .body("user_message", equalTo("Sorry. This idempotency key was already used for a different request"));

        assertThat(countWallets(createWalletRequest.getIdempotencyKey())).isEqualTo(1);
    }

    @Test
    public void createWalletFailsMissingIdempotencyKey() {
        CreateWalletRequest createWalletRequest = walletRequest("KES", "10.00");
        createWalletRequest.setIdempotencyKey(null);

        postWallet(createWalletRequest)
                .then()
                .statusCode(400)
                .body("validation_errors.field", hasItem("idempotencyKey"));
    }

    @Test
    public void createWalletFailureReleasesIdempotencyKey() {
        CreateWalletRequest createWalletRequest = walletRequest("JPY", null);
        postWallet(createWalletRequest).then().statusCode(400);

//        The retry is evaluated again (400), not stuck as in-progress (425)
        postWallet(createWalletRequest)
                .then()
                .statusCode(400);
    }

    @Test
    public void sameIdempotencyKeyFiredConcurrentlyCreatesOneWallet() throws Exception {
        BigDecimal treasuryBefore = getBalance(KES_TREASURY_ID);
        CreateWalletRequest createWalletRequest = walletRequest("KES", "25.00");

        List<Integer> statusCodes = runConcurrently(10, i -> postWallet(createWalletRequest).statusCode());

        assertThat(statusCodes).as("each duplicate is either the result or 'too early'").containsOnly(201, 425);
        assertThat(statusCodes).contains(201);
        assertThat(countWallets(createWalletRequest.getIdempotencyKey())).isEqualTo(1);
        assertThat(getBalance(KES_TREASURY_ID)).as("treasury debited once")
                .isEqualByComparingTo(treasuryBefore.subtract(new BigDecimal("25.00")));
    }

    private int countWallets(UUID idempotencyKey) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM wallets WHERE idempotency_key = ?",
                Integer.class, idempotencyKey);
    }
}
