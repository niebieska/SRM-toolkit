package pl.srm.registrationapi.registration.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.srm.registrationapi.registration.model.RegistrationEditHistory;

import java.util.List;

public interface RegistrationEditHistoryRepository extends JpaRepository<RegistrationEditHistory, Long> {
    List<RegistrationEditHistory> findByRegistrationIdOrderByChangedAtDescIdDesc(Long registrationId);
}
