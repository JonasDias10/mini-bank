package com.simplebank.transaction.exception;

// A request that is well-formed but breaks a business rule (e.g. insufficient funds).
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
