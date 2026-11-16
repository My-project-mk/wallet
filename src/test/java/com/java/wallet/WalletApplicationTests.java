package com.java.wallet;

import com.java.wallet.config.TestcontainersConfiguration;
import com.java.wallet.model.requests.CreateWalletRequest;
import com.java.wallet.model.requests.TransferRequest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

//Base class for all integration tests: boots the full service on a random port against a real PostgreSQL
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
public abstract class WalletApplicationTests {

    public static final UUID KES_TREASURY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @LocalServerPort
    public int port;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    public void setUpRestAssured() {
        RestAssured.port = port;
    }

//    Double-entry invariants must hold after every test, whatever happened during it
    @AfterEach
    public void verifyLedgerIsBalanced() {
        assertThat(jdbcTemplate.queryForObject("SELECT COALESCE(SUM(balance), 0) FROM wallets", BigDecimal.class))
                .as("money is neither created nor destroyed").isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(CASE entry_type WHEN 'CREDIT' THEN amount ELSE -amount END), 0)
                FROM ledger_entries""", BigDecimal.class))
                .as("debits equal credits").isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wallets WHERE NOT system_account AND balance < 0", Integer.class))
                .as("no customer wallet is negative").isZero();
    }

    protected UUID createWallet(String currency, String initialBalance) {
        String walletId = postWallet(walletRequest(currency, initialBalance))
                .then()
                .statusCode(201)
                .extract().path("wallet_id");
        return UUID.fromString(walletId);
    }

    protected CreateWalletRequest walletRequest(String currency, String initialBalance) {
        CreateWalletRequest createWalletRequest = new CreateWalletRequest();
        createWalletRequest.setIdempotencyKey(UUID.randomUUID());
        createWalletRequest.setCurrency(currency);
        createWalletRequest.setInitialBalance(initialBalance == null ? null : new BigDecimal(initialBalance));
        return createWalletRequest;
    }

    protected Response postWallet(CreateWalletRequest createWalletRequest) {
        return given()
                .contentType(ContentType.JSON)
                .body(createWalletRequest)
                .post("/v1/wallets");
    }

    protected BigDecimal getBalance(UUID walletId) {
        String balance = given()
                .get("/v1/wallets/" + walletId + "/balance")
                .then()
                .statusCode(200)
                .extract().path("available_balance");
        return new BigDecimal(balance);
    }

    protected TransferRequest transferRequest(UUID source, UUID destination, String amount) {
        TransferRequest transferRequest = new TransferRequest();
        transferRequest.setIdempotencyKey(UUID.randomUUID());
        transferRequest.setSourceWalletId(source);
        transferRequest.setDestinationWalletId(destination);
        transferRequest.setAmount(new BigDecimal(amount));
        transferRequest.setCurrency("KES");
        return transferRequest;
    }

    protected Response postTransfer(TransferRequest transferRequest) {
        return given()
                .contentType(ContentType.JSON)
                .body(transferRequest)
                .post("/v1/transfers");
    }

//    Releases all tasks at the same instant and returns their results
    protected <T> List<T> runConcurrently(int count, IntFunction<T> task) throws Exception {
        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(count);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(executorService.submit(awaitThenRun(startSignal, task, i)));
            }
            startSignal.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executorService.shutdownNow();
        }
    }

    private <T> Callable<T> awaitThenRun(CountDownLatch startSignal, IntFunction<T> task, int index) {
        return () -> {
            startSignal.await();
            return task.apply(index);
        };
    }
}
