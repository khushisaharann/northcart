package com.northcart.order.repository;

import com.northcart.common.util.SearchText;
import com.northcart.order.entity.Order;
import com.northcart.order.entity.OrderStatus;
import com.northcart.user.entity.User;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin order search built from optional filters with the JPA Criteria API.
 * Each filter is a small reusable Specification; only the ones the admin actually used are
 * combined, so the SQL never contains "(:x IS NULL OR ...)" tricks.
 */
public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<Order> adminSearch(OrderStatus status, String text) {
        List<Specification<Order>> filters = new ArrayList<>();
        if (status != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        String q = SearchText.blankToNull(text);
        if (q != null) {
            String pattern = SearchText.containsPattern(q);
            filters.add((root, query, cb) -> {
                Join<Order, User> user = root.join("user");
                return cb.or(
                        cb.like(cb.lower(root.get("orderNumber")), pattern, SearchText.ESCAPE),
                        cb.like(cb.lower(user.get("email")), pattern, SearchText.ESCAPE),
                        cb.like(cb.lower(user.get("fullName")), pattern, SearchText.ESCAPE));
            });
        }
        return Specification.allOf(filters);
    }
}
