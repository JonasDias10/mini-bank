package com.simplebank.account.service;

import com.simplebank.account.dto.CreateCustomerRequest;
import com.simplebank.account.dto.CustomerResponse;
import com.simplebank.account.exception.DuplicateResourceException;
import com.simplebank.account.exception.ResourceNotFoundException;
import com.simplebank.account.model.Customer;
import com.simplebank.account.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    @Test
    void createCustomerTrimsNameAndNormalizesEmail() {
        when(customerRepository.existsByEmailIgnoreCase("ana@example.com")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerResponse response = customerService.createCustomer(
                new CreateCustomerRequest("  Ana Souza  ", " Ana@Example.COM "));

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(saved.capture());
        assertThat(saved.getValue().getFullName()).isEqualTo("Ana Souza");
        assertThat(saved.getValue().getEmail()).isEqualTo("ana@example.com");
        assertThat(response.fullName()).isEqualTo("Ana Souza");
        assertThat(response.email()).isEqualTo("ana@example.com");
    }

    @Test
    void createCustomerRejectsDuplicateEmail() {
        when(customerRepository.existsByEmailIgnoreCase("ana@example.com")).thenReturn(true);

        assertThatThrownBy(() -> customerService.createCustomer(
                new CreateCustomerRequest("Ana Souza", "ana@example.com")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("ana@example.com");

        verify(customerRepository, never()).save(any());
    }

    @Test
    void getCustomerReturnsCustomer() {
        Customer customer = new Customer("Ana Souza", "ana@example.com");
        ReflectionTestUtils.setField(customer, "id", 1L);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.getCustomer(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.fullName()).isEqualTo("Ana Souza");
    }

    @Test
    void getCustomerThrowsWhenNotFound() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getCustomer(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Customer not found with id 99");
    }
}
