package pl.srm.registrationapi.registration.dto.response;

import java.util.List;

public record PaymentLedgerEntryResponse(Long registrationId, String registrationCode,
                                         List<PaymentRecordResponse> payments,
                                         DiscountRecordResponse discount) {}
