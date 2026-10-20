package com.simplebank.transaction.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simplebank.transaction.dto.AmountRequest;
import com.simplebank.transaction.exception.AccountServiceUnavailableException;
import com.simplebank.transaction.exception.BusinessException;
import com.simplebank.transaction.exception.ErrorResponse;
import com.simplebank.transaction.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.function.Supplier;

@Component
public class AccountClient {

    private static final String DEFAULT_ERROR_MESSAGE = "Account service rejected the request";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AccountClient(RestClient.Builder builder, ObjectMapper objectMapper,
                         @Value("${account-service.url}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultStatusHandler(HttpStatusCode::isError, this::handleError)
                .build();
    }

    public void assertAccountExists(Long accountId) {
        call(() -> restClient.get()
                .uri("/api/accounts/{id}", accountId)
                .retrieve()
                .toBodilessEntity());
    }

    public void credit(Long accountId, BigDecimal amount) {
        postAmount("/api/accounts/{id}/credit", accountId, amount);
    }

    public void debit(Long accountId, BigDecimal amount) {
        postAmount("/api/accounts/{id}/debit", accountId, amount);
    }

    private void postAmount(String uri, Long accountId, BigDecimal amount) {
        call(() -> restClient.post()
                .uri(uri, accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AmountRequest(amount))
                .retrieve()
                .toBodilessEntity());
    }

    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (ResourceAccessException ex) {
            throw new AccountServiceUnavailableException("Account service is unavailable, please try again later");
        }
    }

    private void handleError(HttpRequest request, ClientHttpResponse response) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        String message = readErrorMessage(response);

        if (status.value() == 404) {
            throw new ResourceNotFoundException(message);
        }

        if (status.is4xxClientError()) {
            throw new BusinessException(message);
        }

        throw new AccountServiceUnavailableException("Account service failed to process the request");
    }

    private String readErrorMessage(ClientHttpResponse response) {
        try {
            ErrorResponse error = objectMapper.readValue(response.getBody(), ErrorResponse.class);
            return error.message() != null ? error.message() : DEFAULT_ERROR_MESSAGE;
        } catch (IOException ex) {
            return DEFAULT_ERROR_MESSAGE;
        }
    }
}
