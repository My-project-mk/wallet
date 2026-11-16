package com.java.wallet.model.dto;

import com.java.wallet.model.datatype.EntryType;
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
public class LedgerEntryDto {

    private UUID transferId;
    private EntryType entryType;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal amount;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal balanceAfter;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", shape = JsonFormat.Shape.STRING)
    private LocalDateTime createdDate;
}
