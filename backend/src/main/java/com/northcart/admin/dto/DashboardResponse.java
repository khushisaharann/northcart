package com.northcart.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Everything the admin dashboard shows, in one response (one round trip, consistent numbers).
 * "Revenue" = orders that were paid and not cancelled: CONFIRMED, SHIPPED, DELIVERED.
 */
public record DashboardResponse(
        Summary summary,
        Map<String, Long> ordersByStatus,     // every status present, zero-filled
        List<DailySales> salesByDay,          // one entry per day, gaps filled with 0
        List<TopProduct> topProducts,
        List<LowStockItem> lowStock,
        List<RecentOrder> recentOrders
) {
    public record Summary(BigDecimal revenue, long paidOrders, long totalOrders, long customers,
                          long activeProducts, long lowStockProducts, BigDecimal averageOrderValue) {
    }

    public record DailySales(LocalDate date, BigDecimal revenue, long orders) {
    }

    public record TopProduct(Long productId, String name, String slug, long unitsSold, BigDecimal revenue) {
    }

    public record LowStockItem(Long productId, String name, String sku, int availableQuantity, int lowStockThreshold) {
    }

    public record RecentOrder(String orderNumber, String customerName, String status, BigDecimal total, Instant placedAt) {
    }
}
