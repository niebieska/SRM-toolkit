package pl.srm.registrationapi.registration.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.srm.registrationapi.registration.model.RegistrationStatusHistory;

import java.util.List;

public interface RegistrationStatusHistoryRepository extends JpaRepository<RegistrationStatusHistory, Long> {
    List<RegistrationStatusHistory> findByRegistrationIdOrderByChangedAtDescIdDesc(Long registrationId);
}
