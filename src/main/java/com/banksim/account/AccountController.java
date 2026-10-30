package com.banksim.account;

import com.banksim.account.dto.AccountResponse;
import com.banksim.account.dto.MoneyRequest;
import com.banksim.account.dto.OpenAccountRequest;
import com.banksim.common.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountResponse> listMine(@AuthenticationPrincipal Jwt jwt) {
        return accountService.listMine(CurrentUser.id(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse open(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OpenAccountRequest request) {
        return accountService.openAccount(CurrentUser.id(jwt), request.type());
    }

    @GetMapping("/{id}")
    public AccountResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return accountService.getMine(CurrentUser.id(jwt), id);
    }

    @PostMapping("/{id}/deposit")
    public AccountResponse deposit(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                   @Valid @RequestBody MoneyRequest request) {
        return accountService.deposit(CurrentUser.id(jwt), id, request.amount());
    }

    @PostMapping("/{id}/withdraw")
    public AccountResponse withdraw(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                    @Valid @RequestBody MoneyRequest request) {
        return accountService.withdraw(CurrentUser.id(jwt), id, request.amount());
    }
}
