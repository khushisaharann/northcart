package com.northcart.payment.gateway;

import com.northcart.common.exception.BusinessException;
import com.northcart.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 502: the payment provider failed or was unreachable. Details go to the log, not the client. */
public class PaymentGatewayException extends BusinessException {

    public PaymentGatewayException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, ErrorCode.PAYMENT_GATEWAY_ERROR,
                "The payment provider is not available right now. Please try again.");
        initCause(new RuntimeException(message, cause));
    }
}
