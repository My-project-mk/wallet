package com.java.wallet.web;

import com.java.wallet.WalletApplicationTests;
import com.java.wallet.model.requests.TransferRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class ConcurrentTransferRestControllerTests extends WalletApplicationTests {

    @Test
    public void twoSimultaneousDebitsCannotOverdrawWallet() throws Exception {
        UUID source = createWallet("KES", "60.00");
        UUID destination = createWallet("KES", null);

        List<Integer> statusCodes = runConcurrently(2,
                i -> postTransfer(transferRequest(source, destination, "50.00")).statusCode());

        assertThat(statusCodes).containsExactlyInAnyOrder(201, 400);
        assertThat(getBalance(source)).isEqualByComparingTo("10.00");
        assertThat(getBalance(destination)).isEqualByComparingTo("50.00");
    }

    @Test
    public void manySimultaneousDebitsSucceedUntilBalanceIsExhausted() throws Exception {
        UUID source = createWallet("KES", "100.00");
        UUID destination = createWallet("KES", null);

        List<Integer> statusCodes = runConcurrently(25,
                i -> postTransfer(transferRequest(source, destination, "10.00")).statusCode());

        assertThat(statusCodes).filteredOn(statusCode -> statusCode == 201).hasSize(10);
        assertThat(statusCodes).filteredOn(statusCode -> statusCode == 400).hasSize(15);
        assertThat(getBalance(source)).isEqualByComparingTo("0.00");
        assertThat(getBalance(destination)).isEqualByComparingTo("100.00");
    }

    @Test
    public void opposingTransfersDoNotDeadlock() throws Exception {
        UUID walletA = createWallet("KES", "100.00");
        UUID walletB = createWallet("KES", "100.00");

        List<Integer> statusCodes = runConcurrently(20, i -> i % 2 == 0
                ? postTransfer(transferRequest(walletA, walletB, "5.00")).statusCode()
                : postTransfer(transferRequest(walletB, walletA, "5.00")).statusCode());

        assertThat(statusCodes).containsOnly(201);
        assertThat(getBalance(walletA)).isEqualByComparingTo("100.00");
        assertThat(getBalance(walletB)).isEqualByComparingTo("100.00");
    }

    @Test
    public void sameIdempotencyKeyFiredConcurrentlyMovesMoneyOnce() throws Exception {
        UUID source = createWallet("KES", "100.00");
        UUID destination = createWallet("KES", null);
        TransferRequest transferRequest = transferRequest(source, destination, "10.00");

        List<Integer> statusCodes = runConcurrently(10, i -> postTransfer(transferRequest).statusCode());

        assertThat(statusCodes).as("each duplicate is either the result or 'too early'").containsOnly(201, 425);
        assertThat(statusCodes).contains(201);
        assertThat(getBalance(source)).isEqualByComparingTo("90.00");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transfers WHERE idempotency_key = ?",
                Integer.class, transferRequest.getIdempotencyKey())).isEqualTo(1);
    }
}
