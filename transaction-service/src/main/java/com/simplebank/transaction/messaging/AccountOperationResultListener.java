package com.simplebank.transaction.messaging;

import com.simplebank.transaction.service.TransactionSaga;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AccountOperationResultListener {

    private final TransactionSaga transactionSaga;

    public AccountOperationResultListener(TransactionSaga transactionSaga) {
        this.transactionSaga = transactionSaga;
    }

    @RabbitListener(queues = MessagingConfig.RESULTS_QUEUE)
    public void onResult(AccountOperationResult result) {
        transactionSaga.handleResult(result);
    }
}
