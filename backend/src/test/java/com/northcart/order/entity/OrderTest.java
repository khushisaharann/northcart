package com.northcart.order.entity;

import com.northcart.catalog.entity.Product;
import com.northcart.common.exception.InvalidStateException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain unit tests: business rules on entities need no Spring and no database. */
class OrderTest {

    private static Product product(String name, String sku, String price) {
        Product p = new Product();
        p.setName(name);
        p.setSku(sku);
        p.setPrice(new BigDecimal(price));
        return p;
    }

    @Test
    void orderItemSnapshotsProductAndIsUnaffectedByLaterChanges() {
        Product mouse = product("Wireless Mouse", "SKU-1", "799.00");

        OrderItem item = OrderItem.snapshotOf(mouse, 2);
        mouse.setName("Wireless Mouse v2");
        mouse.setPrice(new BigDecimal("999.00"));

        assertThat(item.getProductName()).isEqualTo("Wireless Mouse");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("799.00");
        assertThat(item.getLineTotal()).isEqualByComparingTo("1598.00");
    }

    @Test
    void totalsAreCalculatedWithBigDecimal() {
        Order order = new Order();
        order.setShippingFee(new BigDecimal("49.00"));
        order.addItem(OrderItem.snapshotOf(product("Mouse", "SKU-1", "0.10"), 1));
        order.addItem(OrderItem.snapshotOf(product("Pad", "SKU-2", "0.20"), 1));

        order.recalculateTotals();

        // With double this would be 0.30000000000000004
        assertThat(order.getSubtotal()).isEqualByComparingTo("0.30");
        assertThat(order.getTotal()).isEqualByComparingTo("49.30");
        assertThat(order.getItems()).allMatch(i -> i.getOrder() == order);
    }

    @Test
    void snapshotRejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> OrderItem.snapshotOf(product("Mouse", "SKU-1", "1.00"), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validStatusFlowIsAllowed() {
        Order order = new Order();
        order.changeStatus(OrderStatus.CONFIRMED);
        order.changeStatus(OrderStatus.SHIPPED);
        order.changeStatus(OrderStatus.DELIVERED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void cancellingRecordsTimestamp() {
        Order order = new Order();
        order.changeStatus(OrderStatus.CANCELLED);
        assertThat(order.getCancelledAt()).isNotNull();
    }

    @Test
    void invalidTransitionIsRejected() {
        Order order = new Order();
        order.changeStatus(OrderStatus.CONFIRMED);
        order.changeStatus(OrderStatus.SHIPPED);

        assertThatThrownBy(() -> order.changeStatus(OrderStatus.CANCELLED))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("SHIPPED");
    }
}
