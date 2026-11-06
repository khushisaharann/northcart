package com.northcart.pricing;

import com.northcart.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Shipping rule shared by the cart (estimate) and checkout (Phase 7, the real charge),
 * so both always show the same number. Values come from app.shipping.* in application.yml.
 */
@Component
@RequiredArgsConstructor
public class ShippingFeeCalculator {

    private final AppProperties properties;

    public BigDecimal feeFor(BigDecimal subtotal) {
        if (subtotal.signum() <= 0) {
            return Money.zero(); // empty cart: nothing to ship
        }
        AppProperties.Shipping shipping = properties.shipping();
        return subtotal.compareTo(shipping.freeShippingThreshold()) >= 0
                ? Money.zero()
                : shipping.flatFee().setScale(2, RoundingMode.HALF_UP);
    }
}
