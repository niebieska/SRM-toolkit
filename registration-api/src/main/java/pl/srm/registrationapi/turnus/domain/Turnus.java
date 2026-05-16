package pl.srm.registrationapi.turnus.domain;


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
        Integer maxAge,
        Integer capacity,

        boolean active,
        boolean registrationOpen

) {}

