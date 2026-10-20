package com.simplebank.account.controller;

import com.simplebank.account.dto.AccountResponse;
import com.simplebank.account.dto.BalanceResponse;
import com.simplebank.account.dto.CreateAccountRequest;
import com.simplebank.account.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return accountService.createAccount(request.customerId());
    }

    @GetMapping("/{id}")
    public AccountResponse getAccount(@PathVariable Long id) {
        return accountService.getAccount(id);
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse getBalance(@PathVariable Long id) {
        return accountService.getBalance(id);
    }
}
