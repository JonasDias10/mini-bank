package com.simplebank.transaction.controller;

import com.simplebank.transaction.dto.AccountOperationRequest;
import com.simplebank.transaction.dto.TransactionResponse;
import com.simplebank.transaction.dto.TransferRequest;
import com.simplebank.transaction.exception.BusinessException;
import com.simplebank.transaction.exception.ResourceNotFoundException;
import com.simplebank.transaction.model.TransactionStatus;
import com.simplebank.transaction.model.TransactionType;
import com.simplebank.transaction.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    private static final BigDecimal AMOUNT = new BigDecimal("100.00");
    private static final Instant CREATED_AT = Instant.parse("2026-10-20T13:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    private ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Pageable capturedPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionService).getTransactionsByAccount(eq(1L), captor.capture());
        return captor.getValue();
    }

    @Test
    void depositReturns202WithPendingTransaction() throws Exception {
        when(transactionService.deposit(new AccountOperationRequest(1L, AMOUNT)))
                .thenReturn(new TransactionResponse(7L, TransactionType.DEPOSIT, TransactionStatus.PENDING, null, 1L, AMOUNT, null, CREATED_AT));

        postJson("/api/transactions/deposit", """
                {"accountId": 1, "amount": 100.00}
                """)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.failureReason").doesNotExist())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.targetAccountId").value(1))
                .andExpect(jsonPath("$.sourceAccountId").doesNotExist());
    }

    @Test
    void depositReturns400WithAllFieldErrors() throws Exception {
        postJson("/api/transactions/deposit", """
                {"amount": 0}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.accountId").value("Account id is required"))
                .andExpect(jsonPath("$.fieldErrors.amount").value("Amount must be at least 0.01"));

        verifyNoInteractions(transactionService);
    }

    @Test
    void depositReturns400WhenBodyIsMissing() throws Exception {
        mockMvc.perform(post("/api/transactions/deposit").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed"));
    }

    @Test
    void transferReturns422WhenAccountsAreTheSame() throws Exception {
        when(transactionService.transfer(any()))
                .thenThrow(new BusinessException("Source and destination accounts must be different"));

        postJson("/api/transactions/transfer", """
                {"fromAccountId": 1, "toAccountId": 1, "amount": 100.00}
                """)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Source and destination accounts must be different"));
    }

    @Test
    void transferReturns202() throws Exception {
        when(transactionService.transfer(new TransferRequest(1L, 2L, AMOUNT)))
                .thenReturn(new TransactionResponse(8L, TransactionType.TRANSFER, TransactionStatus.PENDING, 1L, 2L, AMOUNT, null, CREATED_AT));

        postJson("/api/transactions/transfer", """
                {"fromAccountId": 1, "toAccountId": 2, "amount": 100.00}
                """)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.sourceAccountId").value(1))
                .andExpect(jsonPath("$.targetAccountId").value(2));
    }

    @Test
    void transferReturns400WhenDestinationMissing() throws Exception {
        postJson("/api/transactions/transfer", """
                {"fromAccountId": 1, "amount": 100.00}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.toAccountId").value("Destination account id is required"));
    }

    @Test
    void getTransactionReturns200() throws Exception {
        when(transactionService.getTransaction(7L))
                .thenReturn(new TransactionResponse(7L, TransactionType.WITHDRAWAL, TransactionStatus.PENDING, 1L, null, AMOUNT, null, CREATED_AT));

        mockMvc.perform(get("/api/transactions/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.targetAccountId").doesNotExist());
    }

    @Test
    void getTransactionReturns404() throws Exception {
        when(transactionService.getTransaction(99L))
                .thenThrow(new ResourceNotFoundException("Transaction not found with id 99"));

        mockMvc.perform(get("/api/transactions/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.path").value("/api/transactions/99"));
    }

    @Test
    void getTransactionReturns400WhenIdIsNotANumber() throws Exception {
        mockMvc.perform(get("/api/transactions/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'id'"));
    }

    @Test
    void historyReturns400WhenAccountIdMissing() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parameter 'accountId' is required"));
    }

    @Test
    void historyUsesDefaultPagingNewestFirst() throws Exception {
        when(transactionService.getTransactionsByAccount(eq(1L), any())).thenReturn(List.of(
                new TransactionResponse(2L, TransactionType.WITHDRAWAL, TransactionStatus.PENDING, 1L, null, AMOUNT, null, CREATED_AT)));

        mockMvc.perform(get("/api/transactions").param("accountId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(2));

        Pageable pageable = capturedPageable();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("createdAt")).isNotNull()
                .extracting(Sort.Order::getDirection).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void historyCapsPageSizeAt100() throws Exception {
        mockMvc.perform(get("/api/transactions").param("accountId", "1").param("size", "5000"))
                .andExpect(status().isOk());

        assertThat(capturedPageable().getPageSize()).isEqualTo(100);
    }
}
