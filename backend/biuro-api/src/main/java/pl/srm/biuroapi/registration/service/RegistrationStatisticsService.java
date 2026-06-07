package pl.srm.biuroapi.registration.service;

import org.springframework.stereotype.Service;
import pl.srm.biuroapi.registration.model.RegistrationSummary;
import pl.srm.biuroapi.registration.model.TurnusRegistrationStats;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RegistrationStatisticsService {

    private static final int TURNUS_CAPACITY = 50;

    public List<TurnusRegistrationStats> calculate(
            List<RegistrationSummary> registrations) {

        return registrations.stream()
                .filter(r -> "PARTICIPANT".equalsIgnoreCase(r.registrationType()))
                .collect(Collectors.groupingBy(RegistrationSummary::turnusCode))
                .entrySet()
                .stream()
                .map(entry -> toStats(entry.getKey(), entry.getValue()))
                .toList();
    }
    public TurnusRegistrationStats calculateForTurnus(
            String turnusCode,
            List<RegistrationSummary> registrations
    ) {
        List<RegistrationSummary> relevant = registrations.stream()
                .filter(r -> "PARTICIPANT".equalsIgnoreCase(r.registrationType()))
                .filter(r -> turnusCode.equalsIgnoreCase(r.turnusCode()))
                .filter(r -> isRelevantStatus(r.status()))
                .toList();

        return toStats(turnusCode, relevant);
    }

    private boolean isRelevantStatus(String status) {
        return "ACCEPTED".equalsIgnoreCase(status)
                || "WAITLIST".equalsIgnoreCase(status);
    }

    private TurnusRegistrationStats toStats(String turnusCode, List<RegistrationSummary> registrations) {
        int acceptedMale = count(registrations, "ACCEPTED", "male");
        int acceptedFemale = count(registrations, "ACCEPTED", "female");

        int waitlistMale = count(registrations, "WAITLIST", "male");
        int waitlistFemale = count(registrations, "WAITLIST", "female");

        int accepted = acceptedMale + acceptedFemale;
        int waitlist = waitlistMale + waitlistFemale;

        int occupiedPlaces = accepted;
        int availablePlaces = Math.max(0, TURNUS_CAPACITY - occupiedPlaces);

        return new TurnusRegistrationStats(
                turnusCode,
                occupiedPlaces,
                availablePlaces,
                accepted,
                acceptedMale,
                acceptedFemale,
                waitlist,
                waitlistMale,
                waitlistFemale
        );
    }
    private int count(List<RegistrationSummary> registrations, String status, String gender) {
        return (int) registrations.stream()
                .filter(r -> status.equalsIgnoreCase(r.status()))
                .filter(r -> gender.equalsIgnoreCase(r.gender()))
                .count();
    }
}
