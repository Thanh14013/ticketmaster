package com.ticketmaster.payment.infrastructure.persistence.mapper;

import com.ticketmaster.payment.domain.model.PaymentMethod;
import com.ticketmaster.payment.domain.model.Transaction;
import com.ticketmaster.payment.infrastructure.persistence.entity.TransactionJpaEntity;
import com.ticketmaster.payment.interfaces.dto.TransactionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct Mapper cho {@link Transaction} aggregate.
 *
 * <p>Xử lý embedded {@link PaymentMethod} Value Object:
 * Domain có nested object → JPA entity có các cột pm_* riêng biệt.
 */
@Mapper(componentModel = "spring")
public interface TransactionMapper {

    // ── Domain → JPA Entity ───────────────────────────────────────

    @Mapping(target = "pmStripeId",   source = "paymentMethod.stripePaymentMethodId")
    @Mapping(target = "pmType",       source = "paymentMethod.type")
    @Mapping(target = "pmCardBrand",  source = "paymentMethod.cardBrand")
    @Mapping(target = "pmCardLast4",  source = "paymentMethod.cardLast4")
    @Mapping(target = "pmExpMonth",   source = "paymentMethod.expMonth")
    @Mapping(target = "pmExpYear",    source = "paymentMethod.expYear")
    TransactionJpaEntity toEntity(Transaction transaction);

    // ── JPA Entity → Domain ───────────────────────────────────────

    @Mapping(target = "paymentMethod", expression = """
        java(com.ticketmaster.payment.domain.model.PaymentMethod.builder()
            .stripePaymentMethodId(entity.getPmStripeId())
            .type(entity.getPmType())
            .cardBrand(entity.getPmCardBrand())
            .cardLast4(entity.getPmCardLast4())
            .expMonth(entity.getPmExpMonth())
            .expYear(entity.getPmExpYear())
            .build())
        """)
    Transaction toDomain(TransactionJpaEntity entity);

    // ── Domain → Response DTO ─────────────────────────────────────

    @Mapping(target = "paymentMethodDisplay",
            expression = "java(transaction.getPaymentMethod() != null ? transaction.getPaymentMethod().getDisplayDescription() : null)")
    @Mapping(target = "succeeded", expression = "java(transaction.isSucceeded())")
    TransactionResponse toResponse(Transaction transaction);
}