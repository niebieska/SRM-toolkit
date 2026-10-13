package pl.srm.biuroapi.registration.model;

import java.util.List;

public record TurnusRegistrationStats(String turnusCode, TypeStats participants, TypeStats staff,
        List<StaffRoleStats> staffRoles, long unmappedAcceptedStaff) {
    public TurnusRegistrationStats { staffRoles = List.copyOf(staffRoles); }
    public record TypeStats(int capacity, long accepted, long availablePlaces, long overCapacity,
            long newRegistrations, long waitlist, long rejected,
            long acceptedMale, long acceptedFemale, long waitlistMale, long waitlistFemale) {}
}
