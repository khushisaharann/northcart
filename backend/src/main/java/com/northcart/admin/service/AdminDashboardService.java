package com.northcart.admin.service;

import com.northcart.admin.dto.DashboardResponse;
import com.northcart.admin.repository.AdminStatsRepository;
import com.northcart.pricing.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final AdminStatsRepository stats;
    private final Clock clock;

    /**
     * readOnly + REPEATABLE_READ: every query sees the same snapshot of the database, so the
     * tiles, the chart and the tables can't disagree because an order arrived mid-request.
     */
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public DashboardResponse dashboard(int days) {
        Map<String, Object> s = stats.summary();
        BigDecimal revenue = Money.of((BigDecimal) s.get("revenue"));
        long paidOrders = ((Number) s.get("paid_orders")).longValue();
        BigDecimal aov = paidOrders == 0 ? Money.zero()
                : revenue.divide(BigDecimal.valueOf(paidOrders), 2, RoundingMode.HALF_UP);

        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        LocalDate from = today.minusDays(days - 1L);
        return new DashboardResponse(
                new DashboardResponse.Summary(revenue, paidOrders,
                        ((Number) s.get("total_orders")).longValue(),
                        ((Number) s.get("customers")).longValue(),
                        ((Number) s.get("active_products")).longValue(),
                        ((Number) s.get("low_stock")).longValue(),
                        aov),
                stats.ordersByStatus(),
                stats.salesByDay(from, today),
                stats.topProducts(from, today, 5),   // best sellers of the selected range
                stats.lowStock(8),
                stats.recentOrders(6));
    }
}
