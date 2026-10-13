package pl.srm.biuroapi.registration.service;

import org.springframework.stereotype.Service;
import pl.srm.biuroapi.registration.model.RegistrationSummary;
import pl.srm.biuroapi.registration.model.TurnusRegistrationStats;
import pl.srm.biuroapi.turnus.TurnusCatalogue;
import java.util.List;

@Service
public class RegistrationStatisticsService {
    private final TurnusCatalogue catalogue;
    private final StaffingStatisticsService staffing;
    public RegistrationStatisticsService(TurnusCatalogue catalogue, StaffingStatisticsService staffing) {
        this.catalogue = catalogue;
        this.staffing = staffing;
    }

    public TurnusRegistrationStats calculateForTurnus(String turnusCode, List<RegistrationSummary> registrations) {
        var turnus = catalogue.get(turnusCode);
        var roles = staffing.calculate(turnus, registrations);
        return new TurnusRegistrationStats(turnusCode,
                calculateType(turnusCode, "PARTICIPANT", turnus.participantCapacity(), registrations),
                calculateType(turnusCode, "STAFF", turnus.staffCapacity(), registrations), roles.roles(), roles.unmappedAccepted());
    }

    private TurnusRegistrationStats.TypeStats calculateType(String code, String type, int capacity,
            List<RegistrationSummary> registrations) {
        var relevant = registrations.stream().filter(r -> code.equals(r.turnusCode()))
                .filter(r -> type.equalsIgnoreCase(r.registrationType())).toList();
        long accepted = count(relevant, "ACCEPTED", null);
        return new TurnusRegistrationStats.TypeStats(capacity, accepted,
                Math.max(0, capacity - accepted), Math.max(0, accepted - capacity),
                count(relevant, "NEW", null), count(relevant, "WAITLIST", null), count(relevant, "REJECTED", null),
                count(relevant, "ACCEPTED", "male"), count(relevant, "ACCEPTED", "female"),
                count(relevant, "WAITLIST", "male"), count(relevant, "WAITLIST", "female"));
    }

    private long count(List<RegistrationSummary> registrations, String status, String gender) {
        return registrations.stream().filter(r -> status.equalsIgnoreCase(r.status()))
                .filter(r -> gender == null || gender.equalsIgnoreCase(r.gender())).count();
    }
}
