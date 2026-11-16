package com.java.wallet.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class WalletBalanceDto {

    private UUID walletId;
    private String currency;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal availableBalance;

    private List<LedgerEntryDto> recentEntries;
}
