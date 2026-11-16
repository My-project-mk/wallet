package com.java.wallet.model.requests;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateWalletRequest {

//   Retrying with the same key returns the wallet already created instead of opening (and funding) a second one
    @NotNull
    private UUID idempotencyKey;

    @NotNull
    @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO currency code")
    private String currency;

//   Optional opening deposit - funded from the currency's treasury account.
    @DecimalMin("0.00")
    @Digits(integer = 17, fraction = 2)
    private BigDecimal initialBalance;
}
