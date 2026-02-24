package com.ticketmaster.payment.domain.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Value Object biểu diễn phương thức thanh toán.
 *
 * <p><b>Immutable:</b> Không có setter. Một khi đã tạo, không thể thay đổi.
 * Đây là snapshot tại thời điểm thanh toán – dùng để hiển thị lịch sử.
 *
 * <p>Không lưu full card number – chỉ lưu last4 và brand theo PCI-DSS compliance.
 * Stripe lưu full card info, chúng ta chỉ lưu token reference.
 *
 * <p><b>Lưu DB:</b> Embedded dưới dạng các cột riêng trong bảng {@code transactions}
 * (không tạo bảng riêng – Value Object thường embedded).
 */
@Getter
@Builder
public final class PaymentMethod {

    /**
     * Stripe Payment Method ID (vd: {@code pm_1OxxxxxxxxxxxYYY}).
     * Dùng để charge qua Stripe API.
     */
    private final String stripePaymentMethodId;

    /**
     * Loại phương thức: "card", "bank_transfer", "paypal", v.v.
     */
    private final String type;

    /**
     * Brand thẻ: "visa", "mastercard", "amex", "jcb", v.v.
     * Null nếu không phải card.
     */
    private final String cardBrand;

    /**
     * 4 chữ số cuối của thẻ (vd: "4242").
     * PCI-DSS compliant – không bao giờ lưu full card number.
     */
    private final String cardLast4;

    /**
     * Tháng hết hạn (1-12). Null nếu không phải card.
     */
    private final Integer expMonth;

    /**
     * Năm hết hạn (vd: 2026). Null nếu không phải card.
     */
    private final Integer expYear;

    // ── Domain Methods ─────────────────────────────────────────────

    /**
     * Mô tả hiển thị: "Visa •••• 4242 (12/26)"
     */
    public String getDisplayDescription() {
        if ("card".equals(type) && cardBrand != null && cardLast4 != null) {
            String brand = cardBrand.substring(0, 1).toUpperCase() + cardBrand.substring(1);
            return String.format("%s •••• %s (%02d/%d)", brand, cardLast4, expMonth, expYear);
        }
        return type != null ? type : "Unknown";
    }

    /**
     * Kiểm tra thẻ có còn hạn không.
     */
    public boolean isExpired() {
        if (expMonth == null || expYear == null) return false;
        java.time.YearMonth expiry = java.time.YearMonth.of(expYear, expMonth);
        return expiry.isBefore(java.time.YearMonth.now());
    }

    /**
     * Factory cho card payment.
     */
    public static PaymentMethod card(String stripePaymentMethodId, String brand,
                                     String last4, int expMonth, int expYear) {
        return PaymentMethod.builder()
                .stripePaymentMethodId(stripePaymentMethodId)
                .type("card")
                .cardBrand(brand)
                .cardLast4(last4)
                .expMonth(expMonth)
                .expYear(expYear)
                .build();
    }
}