package com.northcart.address.mapper;

import com.northcart.address.dto.AddressResponse;
import com.northcart.address.dto.CreateAddressRequest;
import com.northcart.address.entity.Address;

public final class AddressMapper {

    private AddressMapper() {
    }

    /** Copies only the fields a client may set; user and default flag are set by the service. */
    public static Address toEntity(CreateAddressRequest r) {
        Address a = new Address();
        a.setFullName(r.fullName().trim());
        a.setPhone(r.phone().trim());
        a.setLine1(r.line1().trim());
        a.setLine2(blankToNull(r.line2()));
        a.setCity(r.city().trim());
        a.setState(r.state().trim());
        a.setPostalCode(r.postalCode().trim());
        if (r.country() != null && !r.country().isBlank()) {
            a.setCountry(r.country().trim());
        }
        return a;
    }

    public static AddressResponse toResponse(Address a) {
        return new AddressResponse(a.getId(), a.getFullName(), a.getPhone(), a.getLine1(), a.getLine2(),
                a.getCity(), a.getState(), a.getPostalCode(), a.getCountry(), a.isDefaultAddress());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
