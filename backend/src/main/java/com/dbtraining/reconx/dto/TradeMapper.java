package com.dbtraining.reconx.dto;

import com.dbtraining.reconx.repository.entity.Trade;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/**
 * ============================================================================
 * TICKET-ADV054 — MapStruct mapper: Trade entity <-> DTO
 *
 * WHAT:    Generates the entity↔DTO conversion at compile time.
 * HOW:     componentModel="spring" → MapStruct emits a @Component bean named
 *          tradeMapper that you can @Autowire.
 * WHY:     Hand-written mappers drift. unmappedTargetPolicy = ERROR fails the
 *          build the moment a field is added to one side and forgotten on the
 *          other.
 * ============================================================================
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TradeMapper {

    @Mapping(source = "instrument.id", target = "instrumentId")
    @Mapping(source = "instrument.symbol", target = "instrumentSymbol")
    @Mapping(source = "counterparty.id", target = "counterpartyId")
    @Mapping(source = "counterparty.name", target = "counterpartyName")
    @Mapping(source = "status", target = "status", qualifiedByName = "statusToString")
    TradeResponse toResponse(Trade trade);

    /**
     * Wire form of the trade. Everything the service owns — the identity, the
     * resolved relations, the lifecycle status and the audit stamps — is left
     * for TradeService to fill in.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "counterparty", ignore = true)
    @Mapping(target = "instrument", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "modifiedAt", ignore = true)
    Trade toEntity(TradeRequest req);

    /**
     * Trade.status is a String column today, so this is the seam where the enum
     * the domain layer will eventually introduce gets rendered for the wire —
     * and it keeps the status mapping explicit under ReportingPolicy.ERROR.
     */
    @Named("statusToString")
    static String statusToString(String status) {
        return status;
    }
}
