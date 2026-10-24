package pl.srm.registrationapi.registration.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.srm.registrationapi.registration.model.Registration;

import java.util.List;
import java.util.Optional;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    List<Registration> findByTurnusCodeAndStatus(String turnusCode, String status);

    long countByTurnusCodeAndRegistrationTypeAndStatus(String turnusCode, String registrationType, String status);

    Optional<Registration> findByRegistrationCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Registration r where r.registrationCode = :code")
    Optional<Registration> findByRegistrationCodeForUpdate(@Param("code") String code);

    boolean existsByTurnusCodeAndPeselHash(String turnusCode, String peselHash);


    List<Registration> findByRegistrationType(String type);
}
