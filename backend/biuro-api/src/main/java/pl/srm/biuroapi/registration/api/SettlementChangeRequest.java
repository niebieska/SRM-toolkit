package pl.srm.biuroapi.registration.api;

import jakarta.validation.constraints.Size;

public record SettlementChangeRequest(@Size(max = 300) String note) {}
