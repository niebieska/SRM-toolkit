package pl.srm.biuroapi.turnus;

import java.time.LocalDate;

public record TurnusDefinition(String turnusCode, String turnusName, String turnusDescription,
        String turnusLocation, SeasonType seasonType, Integer seasonYear,
        LocalDate startDate, LocalDate endDate, Integer minAge, Integer capacity,
        boolean active, boolean registrationOpen, long version) {
    public enum SeasonType { SUMMER, WINTER }
}
