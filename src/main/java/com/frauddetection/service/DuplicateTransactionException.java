package com.frauddetection.service;

public class DuplicateTransactionException extends RuntimeException {

    public DuplicateTransactionException(String transactionId) {
        super("Transaction already processed: " + transactionId);
    }
}
