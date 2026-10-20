package com.simplebank.account.controller;

import com.simplebank.account.dto.AccountResponse;
import com.simplebank.account.dto.CreateCustomerRequest;
import com.simplebank.account.dto.CustomerResponse;
import com.simplebank.account.exception.DuplicateResourceException;
import com.simplebank.account.exception.ResourceNotFoundException;
import com.simplebank.account.service.AccountService;
import com.simplebank.account.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-20T13:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    @MockitoBean
    private AccountService accountService;

    @Test
    void createCustomerReturns201() throws Exception {
        when(customerService.createCustomer(new CreateCustomerRequest("Ana Souza", "ana@example.com")))
                .thenReturn(new CustomerResponse(1L, "Ana Souza", "ana@example.com", CREATED_AT));

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Ana Souza", "email": "ana@example.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.fullName").value("Ana Souza"))
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-20T13:00:00Z"));
    }

    @Test
    void createCustomerReturns400WithFieldErrorsWhenInvalid() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "", "email": "not-an-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.fullName").value("Full name is required"))
                .andExpect(jsonPath("$.fieldErrors.email").value("Email must be valid"));

        verifyNoInteractions(customerService);
    }

    @Test
    void createCustomerReturns409WhenEmailAlreadyExists() throws Exception {
        when(customerService.createCustomer(any()))
                .thenThrow(new DuplicateResourceException("A customer with email ana@example.com already exists"));

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Ana Souza", "email": "ana@example.com"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("A customer with email ana@example.com already exists"));
    }

    @Test
    void getCustomerReturns200() throws Exception {
        when(customerService.getCustomer(1L))
                .thenReturn(new CustomerResponse(1L, "Ana Souza", "ana@example.com", CREATED_AT));

        mockMvc.perform(get("/api/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ana Souza"));
    }

    @Test
    void getCustomerReturns404WithErrorBody() throws Exception {
        when(customerService.getCustomer(99L))
                .thenThrow(new ResourceNotFoundException("Customer not found with id 99"));

        mockMvc.perform(get("/api/customers/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Customer not found with id 99"))
                .andExpect(jsonPath("$.path").value("/api/customers/99"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void getCustomerAccountsReturnsList() throws Exception {
        AccountResponse account = new AccountResponse(
                10L, "1000000000", new BigDecimal("50.00"), 1L, "Ana Souza", CREATED_AT);
        when(accountService.getAccountsByCustomer(1L)).thenReturn(List.of(account));

        mockMvc.perform(get("/api/customers/1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].accountNumber").value("1000000000"))
                .andExpect(jsonPath("$[0].balance").value(50.00));
    }

    @Test
    void unknownEndpointReturns404() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Endpoint not found"));
    }
}
