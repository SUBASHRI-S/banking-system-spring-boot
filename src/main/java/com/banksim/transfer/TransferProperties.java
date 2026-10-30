package com.banksim.transfer;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** BANK-FR-12: maximum total of outgoing transfers per account per UTC day. */
@ConfigurationProperties(prefix = "app.transfer")
public record TransferProperties(@DefaultValue("100000.00") BigDecimal dailyLimit) {
}
