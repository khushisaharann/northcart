package com.northcart.address.service;

import com.northcart.address.dto.AddressResponse;
import com.northcart.address.dto.CreateAddressRequest;
import com.northcart.address.entity.Address;
import com.northcart.address.mapper.AddressMapper;
import com.northcart.address.repository.AddressRepository;
import com.northcart.common.exception.InvalidStateException;
import com.northcart.common.exception.ResourceNotFoundException;
import com.northcart.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressService {

    static final int MAX_ADDRESSES_PER_USER = 10;

    private final AddressRepository addressRepository;
    private final UserService userService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<AddressResponse> listForUser(Long userId) {
        return addressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(userId).stream()
                .map(AddressMapper::toResponse)
                .toList();
    }

    @Transactional
    public AddressResponse create(Long userId, CreateAddressRequest request) {
        long existing = addressRepository.countByUserId(userId);
        if (existing >= MAX_ADDRESSES_PER_USER) {
            throw new InvalidStateException("You can save at most " + MAX_ADDRESSES_PER_USER + " addresses");
        }

        // The first address is always the default; later ones only if asked.
        boolean makeDefault = existing == 0 || Boolean.TRUE.equals(request.makeDefault());
        if (makeDefault && existing > 0) {
            addressRepository.clearDefaultForUser(userId, clock.instant());
        }

        Address address = AddressMapper.toEntity(request);
        address.setUser(userService.getReference(userId));
        address.setDefaultAddress(makeDefault);
        addressRepository.save(address);
        return AddressMapper.toResponse(address);
    }

    /**
     * Used by checkout (Phase 7). Looks up by id AND owner, so passing another
     * customer's address id gives "not found" rather than leaking their address.
     */
    @Transactional(readOnly = true)
    public Address getOwnedAddress(Long userId, Long addressId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", addressId));
    }
}
