package com.simplebank.account.controller;

import com.simplebank.account.dto.AccountResponse;
import com.simplebank.account.dto.BalanceResponse;
import com.simplebank.account.exception.ResourceNotFoundException;
import com.simplebank.account.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    private static AccountResponse account(String balance) {
        return new AccountResponse(10L, "1000000000", new BigDecimal(balance), 1L, "Ana Souza",
                Instant.parse("2026-10-20T13:00:00Z"));
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void createAccountReturns201() throws Exception {
        when(accountService.createAccount(1L)).thenReturn(account("0.00"));

        postJson("/api/accounts", """
                {"customerId": 1}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.accountNumber").value("1000000000"))
                .andExpect(jsonPath("$.customerId").value(1));
    }

    @Test
    void createAccountReturns400WhenCustomerIdMissing() throws Exception {
        postJson("/api/accounts", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.customerId").value("Customer id is required"));

        verifyNoInteractions(accountService);
    }

    @Test
    void createAccountReturns400WhenCustomerIdNotPositive() throws Exception {
        postJson("/api/accounts", """
                {"customerId": -1}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.customerId").value("Customer id must be positive"));
    }

    @Test
    void createAccountReturns404WhenCustomerDoesNotExist() throws Exception {
        when(accountService.createAccount(99L))
                .thenThrow(new ResourceNotFoundException("Customer not found with id 99"));

        postJson("/api/accounts", """
                {"customerId": 99}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Customer not found with id 99"));
    }

    @Test
    void createAccountReturns400WhenBodyIsMalformed() throws Exception {
        postJson("/api/accounts", "{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed"));
    }

    @Test
    void getAccountReturns200() throws Exception {
        when(accountService.getAccount(10L)).thenReturn(account("99.90"));

        mockMvc.perform(get("/api/accounts/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(99.90))
                .andExpect(jsonPath("$.customerName").value("Ana Souza"));
    }

    @Test
    void getAccountReturns400WhenIdIsNotANumber() throws Exception {
        mockMvc.perform(get("/api/accounts/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'id'"));
    }

    @Test
    void getBalanceReturns200() throws Exception {
        when(accountService.getBalance(10L))
                .thenReturn(new BalanceResponse(10L, "1000000000", new BigDecimal("42.00")));

        mockMvc.perform(get("/api/accounts/10/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(10))
                .andExpect(jsonPath("$.balance").value(42.00));
    }

    @Test
    void unexpectedErrorReturns500WithoutInternalDetails() throws Exception {
        when(accountService.getAccount(10L)).thenThrow(new IllegalStateException("connection to db-host:5432 failed"));

        mockMvc.perform(get("/api/accounts/10"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("db-host"))));
    }
}
