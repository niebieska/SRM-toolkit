package pl.srm.biuroapi.turnus;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record TurnusDefinition(String turnusCode, String turnusName, String turnusDescription,
        String turnusLocation, SeasonType seasonType, Integer seasonYear,
        LocalDate startDate, LocalDate endDate, Integer minAge, Integer participantCapacity, Integer staffCapacity,
        boolean active, boolean participantRegistrationOpen, boolean staffRegistrationOpen, long version,
        List<StaffRequirement> staffRequirements, BigDecimal participantFee, List<StaffFee> staffFees,
        boolean feesConfigured) {
    public TurnusDefinition {
        staffRequirements = List.copyOf(staffRequirements);
        participantFee = Objects.requireNonNullElse(participantFee, BigDecimal.ZERO);
        staffFees = List.copyOf(staffFees);
    }
    public TurnusDefinition(String turnusCode, String turnusName, String turnusDescription,
            String turnusLocation, SeasonType seasonType, Integer seasonYear,
            LocalDate startDate, LocalDate endDate, Integer minAge, Integer participantCapacity, Integer staffCapacity,
            boolean active, boolean participantRegistrationOpen, boolean staffRegistrationOpen, long version,
            List<StaffRequirement> staffRequirements) {
        this(turnusCode, turnusName, turnusDescription, turnusLocation, seasonType, seasonYear, startDate, endDate,
                minAge, participantCapacity, staffCapacity, active, participantRegistrationOpen,
                staffRegistrationOpen, version, staffRequirements, BigDecimal.ZERO, List.of(), false);
    }
    public enum SeasonType { SUMMER, WINTER }
}
