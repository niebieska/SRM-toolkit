package pl.srm.registrationapi.turnus.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Turnus(

        String turnusCode,
        String turnusName,
        String turnusDescription,

        String turnusLocation,

        SeasonType seasonType,
        Integer seasonYear,

        LocalDate startDate,
        LocalDate endDate,

        Integer minAge,
        Integer participantCapacity,
        Integer staffCapacity,

        boolean active,
        boolean participantRegistrationOpen,
        boolean staffRegistrationOpen,
        @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
        pl.srm.registrationapi.season.StaffCatalogue staffCatalogue

) {
    // Kept for existing isolated Turnus tests and the non-runtime JSON stub.
    public Turnus(String code, String name, String description, String location, SeasonType seasonType,
            Integer year, LocalDate start, LocalDate end, Integer minAge, Integer participantCapacity,
            Integer staffCapacity, boolean active, boolean participantOpen, boolean staffOpen) {
        this(code, name, description, location, seasonType, year, start, end, minAge,
                participantCapacity, staffCapacity, active, participantOpen, staffOpen, null);
    }
}

