package com.banksim.transfer;

import com.banksim.common.CurrentUser;
import com.banksim.transfer.dto.TransferRequest;
import com.banksim.transfer.dto.TransferResponse;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse transfer(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Optional. Sending the same key twice creates one transfer.")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        return transferService.transfer(CurrentUser.id(jwt), request.fromAccountId(), request.toAccountNumber(),
                request.amount(), request.description(), idempotencyKey);
    }
}
