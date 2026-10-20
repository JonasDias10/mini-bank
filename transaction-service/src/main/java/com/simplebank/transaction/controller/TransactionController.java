package com.simplebank.transaction.controller;

import com.simplebank.transaction.dto.AccountOperationRequest;
import com.simplebank.transaction.dto.TransactionResponse;
import com.simplebank.transaction.dto.TransferRequest;
import com.simplebank.transaction.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/deposit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TransactionResponse deposit(@Valid @RequestBody AccountOperationRequest request) {
        return transactionService.deposit(request);
    }

    @PostMapping("/withdraw")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TransactionResponse withdraw(@Valid @RequestBody AccountOperationRequest request) {
        return transactionService.withdraw(request);
    }

    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TransactionResponse transfer(@Valid @RequestBody TransferRequest request) {
        return transactionService.transfer(request);
    }

    @GetMapping("/{id}")
    public TransactionResponse getTransaction(@PathVariable Long id) {
        return transactionService.getTransaction(id);
    }

    @GetMapping
    public List<TransactionResponse> getTransactionsByAccount(
            @RequestParam Long accountId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return transactionService.getTransactionsByAccount(accountId, pageable);
    }
}
