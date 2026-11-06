package com.northcart.order.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @CsvSource({
            "PLACED,    CONFIRMED, true",
            "PLACED,    CANCELLED, true",
            "PLACED,    SHIPPED,   false",
            "CONFIRMED, SHIPPED,   true",
            "CONFIRMED, CANCELLED, true",
            "SHIPPED,   DELIVERED, true",
            "SHIPPED,   CANCELLED, false",
            "DELIVERED, PLACED,    false",
            "CANCELLED, CONFIRMED, false"
    })
    void transitions(OrderStatus from, OrderStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @Test
    void onlyPlacedAndConfirmedAreCancellable() {
        assertThat(OrderStatus.PLACED.isCancellable()).isTrue();
        assertThat(OrderStatus.CONFIRMED.isCancellable()).isTrue();
        assertThat(OrderStatus.SHIPPED.isCancellable()).isFalse();
        assertThat(OrderStatus.DELIVERED.isCancellable()).isFalse();
        assertThat(OrderStatus.CANCELLED.isCancellable()).isFalse();
    }
}
