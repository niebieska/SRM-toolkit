package pl.srm.registrationapi.registration.repository;

import org.springframework.stereotype.Repository;
import pl.srm.registrationapi.registration.domain.Registration;

import java.util.ArrayList;
import java.util.List;

@Repository
public class StaffRegistrationRepository {

    private final List<Registration> storage = new ArrayList<>();

    public void save(Registration registration) {
        storage.add(registration);
    }

    public List<Registration> findAll() {
        return storage;
    }

    public boolean exists(String turnusCode, String personKey) {
        return storage.stream()
                .anyMatch(r ->
                        r.turnusCode().equals(turnusCode)
                                && r.personKey().equals(personKey)
                );
    }

    public int count() {
        return storage.size();
    }


    public int countByTurnus(String turnusCode) {
        return (int) storage.stream()
                .filter(r -> r.turnusCode().equals(turnusCode))
                .count();
    }

}
