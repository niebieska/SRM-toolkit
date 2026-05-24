package pl.srm.registrationapi.registration.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.srm.registrationapi.registration.model.Registration;

import java.util.List;
import java.util.Optional;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    Optional<Registration> findByRegistrationCode(String code);

    boolean existsByTurnusCodeAndPeselHash(String turnusCode, String peselHash);

    int countByTurnusCode(String turnusCode);

    List<Registration> findByRegistrationType(String type);
}
