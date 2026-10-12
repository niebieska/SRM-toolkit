package pl.srm.biuroapi.registration.model;

public record TurnusRegistrationStats(String turnusCode, TypeStats participants, TypeStats staff) {
    public record TypeStats(int capacity, long accepted, long availablePlaces, long overCapacity,
            long newRegistrations, long waitlist, long rejected,
            long acceptedMale, long acceptedFemale, long waitlistMale, long waitlistFemale) {}
}
