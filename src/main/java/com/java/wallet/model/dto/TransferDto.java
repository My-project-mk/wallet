package com.java.wallet.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TransferDto {

    public static final String STATUS_COMPLETED = "COMPLETED";

    private UUID transferId;
    private UUID idempotencyKey;
    private UUID sourceWalletId;
    private UUID destinationWalletId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal amount;

    private String currency;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", shape = JsonFormat.Shape.STRING)
    private LocalDateTime createdDate;
}
