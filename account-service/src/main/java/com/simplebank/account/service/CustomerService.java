package com.simplebank.account.service;

import com.simplebank.account.dto.CreateCustomerRequest;
import com.simplebank.account.dto.CustomerResponse;
import com.simplebank.account.exception.DuplicateResourceException;
import com.simplebank.account.exception.ResourceNotFoundException;
import com.simplebank.account.model.Customer;
import com.simplebank.account.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public CustomerResponse createCustomer(CreateCustomerRequest request) {
        String email = request.email().trim().toLowerCase();

        if (customerRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("A customer with email " + email + " already exists");
        }

        Customer customer = customerRepository.save(new Customer(request.fullName().trim(), email));
        return CustomerResponse.from(customer);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(Long id) {
        return CustomerResponse.from(findCustomer(id));
    }

    @Transactional(readOnly = true)
    public Customer findCustomer(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id " + id));
    }
}
