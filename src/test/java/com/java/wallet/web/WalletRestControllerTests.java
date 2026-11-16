package com.java.wallet.web;

import com.java.wallet.WalletApplicationTests;
import com.java.wallet.model.entity.Wallet;
import com.java.wallet.model.requests.CreateWalletRequest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

public class WalletRestControllerTests extends WalletApplicationTests {

    @Test
    public void createWalletWorks() {
        CreateWalletRequest createWalletRequest = new CreateWalletRequest();
        createWalletRequest.setIdempotencyKey(UUID.randomUUID());
        createWalletRequest.setCurrency("KES");
        createWalletRequest.setInitialBalance(new BigDecimal("100.00"));

        given()
                .contentType(ContentType.JSON)
                .body(createWalletRequest)
                .post("/v1/wallets")
                .then()
                .statusCode(201)
                .body("currency", equalTo("KES"))
                .body("balance", equalTo("100.00"));
    }

    @Test
    public void createWalletWithoutOpeningBalanceWorks() {
        UUID walletId = createWallet("USD", null);

        given()
                .get("/v1/wallets/" + walletId + "/balance")
                .then()
                .statusCode(200)
                .body("available_balance", equalTo("0.00"))
                .body("recent_entries", empty());
    }

    @Test
    public void createWalletFailsUnsupportedCurrency() {
        CreateWalletRequest createWalletRequest = new CreateWalletRequest();
        createWalletRequest.setIdempotencyKey(UUID.randomUUID());
        createWalletRequest.setCurrency("JPY");

        given()
                .contentType(ContentType.JSON)
                .body(createWalletRequest)
                .post("/v1/wallets")
                .then()
                .statusCode(400)
                .body("developer_message", containsString("not supported"));
    }

    @Test
    public void createWalletFailsInvalidCurrency() {
        CreateWalletRequest createWalletRequest = new CreateWalletRequest();
        createWalletRequest.setIdempotencyKey(UUID.randomUUID());
        createWalletRequest.setCurrency("kes");

        given()
                .contentType(ContentType.JSON)
                .body(createWalletRequest)
                .post("/v1/wallets")
                .then()
                .statusCode(400)
                .body("validation_errors[0].field", equalTo("currency"));
    }

    @Test
    public void createWalletFailsNegativeOpeningBalance() {
        CreateWalletRequest createWalletRequest = new CreateWalletRequest();
        createWalletRequest.setIdempotencyKey(UUID.randomUUID());
        createWalletRequest.setCurrency("KES");
        createWalletRequest.setInitialBalance(new BigDecimal("-1.00"));

        given()
                .contentType(ContentType.JSON)
                .body(createWalletRequest)
                .post("/v1/wallets")
                .then()
                .statusCode(400);
    }

    @Test
    public void getWalletBalanceShowsOpeningDepositAsCredit() {
        UUID walletId = createWallet("KES", "250.75");

        given()
                .get("/v1/wallets/" + walletId + "/balance")
                .then()
                .statusCode(200)
                .body("available_balance", equalTo("250.75"))
                .body("recent_entries", hasSize(1))
                .body("recent_entries[0].entry_type", equalTo("CREDIT"))
                .body("recent_entries[0].amount", equalTo("250.75"))
                .body("recent_entries[0].balance_after", equalTo("250.75"));
    }

    @Test
    public void getWalletBalanceFailsUnknownWallet() {
        given()
                .get("/v1/wallets/" + UUID.randomUUID() + "/balance")
                .then()
                .statusCode(404)
                .body("user_message", equalTo("Sorry. Wallet not found"));
    }

    @Test
    public void getWalletBalanceFailsInvalidLimit() {
        UUID walletId = createWallet("KES", null);

        given()
                .get("/v1/wallets/" + walletId + "/balance?limit=0")
                .then()
                .statusCode(400);
    }

    @Test
    public void openingDepositIsDebitedFromTreasury() {
        BigDecimal treasuryBefore = getBalance(KES_TREASURY_ID);

        createWallet("KES", "40.00");

        assertThat(getBalance(KES_TREASURY_ID)).isEqualByComparingTo(treasuryBefore.subtract(new BigDecimal("40.00")));
        given()
                .get("/v1/wallets/" + KES_TREASURY_ID + "/balance")
                .then()
                .body("recent_entries[0].entry_type", equalTo("DEBIT"));
    }
}
