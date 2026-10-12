package pl.srm.biuroapi.turnus;

import java.time.LocalDate;

public record TurnusDefinition(String turnusCode, String turnusName, String turnusDescription,
        String turnusLocation, SeasonType seasonType, Integer seasonYear,
        LocalDate startDate, LocalDate endDate, Integer minAge, Integer participantCapacity, Integer staffCapacity,
        boolean active, boolean participantRegistrationOpen, boolean staffRegistrationOpen, long version) {
    public enum SeasonType { SUMMER, WINTER }
}
