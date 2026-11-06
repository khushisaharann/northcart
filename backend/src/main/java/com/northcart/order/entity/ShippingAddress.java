package com.northcart.order.entity;

import com.northcart.address.entity.Address;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Value object embedded in the orders table (columns ship_*). It is a COPY of the
 * customer's address at checkout time, so later editing or deleting the saved Address
 * never changes where a past order was shipped. Immutable: no setters.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA only
public class ShippingAddress {

    @Column(name = "ship_full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "ship_phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "ship_line1", nullable = false, length = 200)
    private String line1;

    @Column(name = "ship_line2", length = 200)
    private String line2;

    @Column(name = "ship_city", nullable = false, length = 100)
    private String city;

    @Column(name = "ship_state", nullable = false, length = 100)
    private String state;

    @Column(name = "ship_postal_code", nullable = false, length = 20)
    private String postalCode;

    @Column(name = "ship_country", nullable = false, length = 60)
    private String country;

    public static ShippingAddress snapshotOf(Address a) {
        ShippingAddress s = new ShippingAddress();
        s.fullName = a.getFullName();
        s.phone = a.getPhone();
        s.line1 = a.getLine1();
        s.line2 = a.getLine2();
        s.city = a.getCity();
        s.state = a.getState();
        s.postalCode = a.getPostalCode();
        s.country = a.getCountry();
        return s;
    }
}
