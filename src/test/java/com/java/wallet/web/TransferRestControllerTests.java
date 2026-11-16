package com.java.wallet.web;

import com.java.wallet.WalletApplicationTests;
import com.java.wallet.model.requests.TransferRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

public class TransferRestControllerTests extends WalletApplicationTests {

    @Test
    public void createTransferWorks() {
        UUID source = createWallet("KES", "100.00");
        UUID destination = createWallet("KES", "5.00");
        TransferRequest transferRequest = transferRequest(source, destination, "40.25");

        String transferId = postTransfer(transferRequest)
                .then()
                .statusCode(201)
                .header(TransferRestController.REPLAYED_HEADER, "false")
                .body("status", equalTo("COMPLETED"))
                .body("amount", equalTo("40.25"))
                .body("idempotency_key", equalTo(transferRequest.getIdempotencyKey().toString()))
                .extract().path("transfer_id");

        assertThat(getBalance(source)).isEqualByComparingTo("59.75");
        assertThat(getBalance(destination)).isEqualByComparingTo("45.25");
        assertTransferHasDebitAndCredit(UUID.fromString(transferId));
    }

    @Test
    public void createTransferOfEntireBalanceWorks() {
        UUID source = createWallet("KES", "60.00");
        UUID destination = createWallet("KES", null);

        postTransfer(transferRequest(source, destination, "60.00")).then().statusCode(201);

        assertThat(getBalance(source)).isEqualByComparingTo("0.00");
    }

    @Test
    public void createTransferWritesEntriesNewestFirst() {
        UUID source = createWallet("KES", "100.00");
        UUID destination = createWallet("KES", null);

        postTransfer(transferRequest(source, destination, "30.00")).then().statusCode(201);

        given()
                .get("/v1/wallets/" + source + "/balance")
                .then()
                .body("recent_entries.entry_type", contains("DEBIT", "CREDIT"))
                .body("recent_entries[0].balance_after", equalTo("70.00"));
    }

    @Test
    public void createTransferFailsInsufficientFunds() {
        UUID source = createWallet("KES", "10.00");
        UUID destination = createWallet("KES", null);

        postTransfer(transferRequest(source, destination, "10.01"))
                .then()
                .statusCode(400)
                .body("user_message", equalTo("Sorry. Insufficient funds"));

        assertThat(getBalance(source)).isEqualByComparingTo("10.00");
        given()
                .get("/v1/wallets/" + destination + "/balance")
                .then()
                .body("available_balance", equalTo("0.00"))
                .body("recent_entries", empty());
    }

    @Test
    public void createTransferFailureReleasesIdempotencyKey() {
        UUID source = createWallet("KES", "10.00");
        UUID destination = createWallet("KES", null);
        TransferRequest transferRequest = transferRequest(source, destination, "50.00");
        postTransfer(transferRequest).then().statusCode(400);

        // The retry is evaluated again (400), not stuck as in-progress (425).
        postTransfer(transferRequest)
                .then()
                .statusCode(400);
    }

    @Test
    public void createTransferFailsZeroOrNegativeAmount() {
        UUID source = createWallet("KES", "10.00");
        UUID destination = createWallet("KES", null);

        postTransfer(transferRequest(source, destination, "0.00")).then().statusCode(400);
        postTransfer(transferRequest(source, destination, "-5.00")).then().statusCode(400);
    }

    @Test
    public void createTransferFailsSubCentAmount() {
        UUID source = createWallet("KES", "10.00");
        UUID destination = createWallet("KES", null);

        postTransfer(transferRequest(source, destination, "1.001"))
                .then()
                .statusCode(400)
                .body("validation_errors.field", hasItem("amount"));
    }

    @Test
    public void createTransferFailsSameWallet() {
        UUID wallet = createWallet("KES", "10.00");

        postTransfer(transferRequest(wallet, wallet, "1.00"))
                .then()
                .statusCode(400)
                .body("validation_errors.field", hasItem("distinctWallets"));
    }

    @Test
    public void createTransferFailsMissingIdempotencyKey() {
        UUID source = createWallet("KES", "10.00");
        UUID destination = createWallet("KES", null);
        TransferRequest transferRequest = transferRequest(source, destination, "1.00");
        transferRequest.setIdempotencyKey(null);

        postTransfer(transferRequest)
                .then()
                .statusCode(400)
                .body("validation_errors.field", hasItem("idempotencyKey"));
    }

    @Test
    public void createTransferFailsCurrencyMismatch() {
        UUID source = createWallet("KES", "10.00");
        UUID destination = createWallet("USD", null);

        postTransfer(transferRequest(source, destination, "1.00"))
                .then()
                .statusCode(400)
                .body("user_message", equalTo("Sorry. Currency does not match the wallet currency"));
    }

    @Test
    public void createTransferFailsUnknownWallet() {
        UUID source = createWallet("KES", "10.00");

        postTransfer(transferRequest(source, UUID.randomUUID(), "1.00"))
                .then()
                .statusCode(404);
    }

    @Test
    public void createTransferFailsFromTreasury() {
        UUID destination = createWallet("KES", null);

        postTransfer(transferRequest(KES_TREASURY_ID, destination, "1.00"))
                .then()
                .statusCode(404);

        assertThat(getBalance(destination)).isEqualByComparingTo("0.00");
    }

    private void assertTransferHasDebitAndCredit(UUID transferPublicId) {
        List<Map<String, Object>> entries = jdbcTemplate.queryForList("""
                SELECT e.entry_type, e.amount FROM ledger_entries e
                JOIN transfers t ON t.id = e.transfer_id
                WHERE t.public_id = ? ORDER BY e.entry_type""", transferPublicId);
        assertThat(entries).extracting(entry -> entry.get("entry_type")).containsExactly("CREDIT", "DEBIT");
        assertThat(entries).allSatisfy(entry ->
                assertThat((BigDecimal) entry.get("amount")).isEqualByComparingTo("40.25"));
    }
}
