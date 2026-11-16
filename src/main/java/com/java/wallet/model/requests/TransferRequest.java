package com.java.wallet.model.requests;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TransferRequest {

    @NotNull
    private UUID idempotencyKey;

    @NotNull
    private UUID sourceWalletId;

    @NotNull
    private UUID destinationWalletId;

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 17, fraction = 2)
    private BigDecimal amount;

    @NotNull
    @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO currency code")
    private String currency;

    @JsonIgnore
    @AssertTrue(message = "source and destination wallets must be different")
    public boolean isDistinctWallets() {
        return !Objects.equals(sourceWalletId, destinationWalletId);
    }
}
