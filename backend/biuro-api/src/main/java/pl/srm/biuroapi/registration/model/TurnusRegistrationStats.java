package pl.srm.biuroapi.registration.model;

public record TurnusRegistrationStats(
        String turnusCode,

        int occupiedPlaces,
        int availablePlaces,

        int accepted,
        int acceptedMale,
        int acceptedFemale,

        int waitlist,
        int waitlistMale,
        int waitlistFemale
) {
}