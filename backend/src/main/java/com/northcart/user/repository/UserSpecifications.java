package com.northcart.user.repository;

import com.northcart.common.util.SearchText;
import com.northcart.user.entity.Role;
import com.northcart.user.entity.RoleName;
import com.northcart.user.entity.User;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class UserSpecifications {

    private UserSpecifications() {
    }

    /** Customers only, optionally matching name or email. */
    public static Specification<User> customers(String text) {
        List<Specification<User>> filters = new ArrayList<>();
        filters.add((root, query, cb) -> {
            Join<User, Role> role = root.join("roles");
            return cb.equal(role.get("name"), RoleName.CUSTOMER);
        });
        String q = SearchText.blankToNull(text);
        if (q != null) {
            String pattern = SearchText.containsPattern(q);
            filters.add((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("email")), pattern, SearchText.ESCAPE),
                    cb.like(cb.lower(root.get("fullName")), pattern, SearchText.ESCAPE)));
        }
        return Specification.allOf(filters);
    }
}
