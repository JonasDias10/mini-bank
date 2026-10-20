package com.simplebank.transaction.client;

import com.simplebank.transaction.exception.AccountServiceUnavailableException;
import com.simplebank.transaction.exception.BusinessException;
import com.simplebank.transaction.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AccountClientTest {

    private static final String BASE_URL = "http://account-service";

    private MockRestServiceServer server;
    private AccountClient accountClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        accountClient = new AccountClient(builder, Jackson2ObjectMapperBuilder.json().build(), BASE_URL);
    }

    @AfterEach
    void verifyAllRequestsWereMade() {
        server.verify();
    }

    private static String errorBody(int status, String message) {
        return """
                {"timestamp": "2026-10-20T13:00:00Z", "status": %d, "error": "x", "message": "%s", "path": "/x"}
                """.formatted(status, message);
    }

    @Test
    void creditPostsAmountToCreditEndpoint() {
        server.expect(requestTo(BASE_URL + "/api/accounts/1/credit"))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"amount": 100.50}
                        """))
                .andRespond(withSuccess());

        accountClient.credit(1L, new BigDecimal("100.50"));
    }

    @Test
    void debitPostsAmountToDebitEndpoint() {
        server.expect(requestTo(BASE_URL + "/api/accounts/1/debit"))
                .andExpect(method(POST))
                .andRespond(withSuccess());

        accountClient.debit(1L, new BigDecimal("20.00"));
    }

    @Test
    void assertAccountExistsFetchesAccount() {
        server.expect(requestTo(BASE_URL + "/api/accounts/2"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        accountClient.assertAccountExists(2L);
    }

    @Test
    void notFoundBecomesResourceNotFoundWithRemoteMessage() {
        server.expect(requestTo(BASE_URL + "/api/accounts/9"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(errorBody(404, "Account not found with id 9")));

        assertThatThrownBy(() -> accountClient.assertAccountExists(9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Account not found with id 9");
    }

    @Test
    void unprocessableEntityBecomesBusinessExceptionWithRemoteMessage() {
        server.expect(requestTo(BASE_URL + "/api/accounts/1/debit"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(errorBody(422, "Insufficient funds in account 1000000000")));

        assertThatThrownBy(() -> accountClient.debit(1L, BigDecimal.TEN))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Insufficient funds in account 1000000000");
    }

    @Test
    void clientErrorWithoutJsonBodyUsesDefaultMessage() {
        server.expect(requestTo(BASE_URL + "/api/accounts/1/debit"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("not json"));

        assertThatThrownBy(() -> accountClient.debit(1L, BigDecimal.TEN))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Account service rejected the request");
    }

    @Test
    void serverErrorBecomesServiceUnavailable() {
        server.expect(requestTo(BASE_URL + "/api/accounts/1/credit"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> accountClient.credit(1L, BigDecimal.TEN))
                .isInstanceOf(AccountServiceUnavailableException.class)
                .hasMessage("Account service failed to process the request");
    }

    @Test
    void connectionFailureBecomesServiceUnavailable() {
        server.expect(requestTo(BASE_URL + "/api/accounts/1/credit"))
                .andRespond(withException(new ConnectException("Connection refused")));

        assertThatThrownBy(() -> accountClient.credit(1L, BigDecimal.TEN))
                .isInstanceOf(AccountServiceUnavailableException.class)
                .hasMessage("Account service is unavailable, please try again later");
    }
}
