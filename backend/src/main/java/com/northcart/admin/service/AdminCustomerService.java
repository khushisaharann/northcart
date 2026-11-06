package com.northcart.admin.service;

import com.northcart.admin.dto.AdminCustomerResponse;
import com.northcart.admin.dto.AdminCustomerSearchParams;
import com.northcart.admin.repository.AdminStatsRepository;
import com.northcart.admin.repository.AdminStatsRepository.CustomerStats;
import com.northcart.common.dto.PageResponse;
import com.northcart.pricing.Money;
import com.northcart.user.entity.User;
import com.northcart.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCustomerService {

    private final UserService userService;
    private final AdminStatsRepository stats;

    /** Two queries per page: the customers, then their order stats for exactly those ids. */
    @Transactional(readOnly = true)
    public PageResponse<AdminCustomerResponse> search(AdminCustomerSearchParams params) {
        var page = userService.searchCustomers(params.q(), params.pageOrDefault(), params.sizeOrDefault());
        Map<Long, CustomerStats> byUser = stats.customerStats(page.getContent().stream().map(User::getId).toList());
        return PageResponse.from(page, u -> toResponse(u, byUser.getOrDefault(u.getId(), CustomerStats.NONE)));
    }

    @Transactional
    public AdminCustomerResponse setEnabled(Long userId, boolean enabled) {
        User user = userService.setCustomerEnabled(userId, enabled);
        log.info("Admin {} customer id={}", enabled ? "enabled" : "disabled", userId);
        CustomerStats s = stats.customerStats(java.util.List.of(userId)).getOrDefault(userId, CustomerStats.NONE);
        return toResponse(user, s);
    }

    private static AdminCustomerResponse toResponse(User u, CustomerStats s) {
        return new AdminCustomerResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.isEnabled(),
                s.orders(), Money.of(s.spent()), u.getCreatedAt());
    }
}
