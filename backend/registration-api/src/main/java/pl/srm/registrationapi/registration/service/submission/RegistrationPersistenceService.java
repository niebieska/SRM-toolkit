package pl.srm.registrationapi.registration.service.submission;

import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.model.Registration;
import pl.srm.registrationapi.registration.model.RegistrationStatus;
import pl.srm.registrationapi.registration.model.RegistrationType;
import pl.srm.registrationapi.registration.parser.RegistrationContext;
import pl.srm.registrationapi.registration.repository.RegistrationRepository;
import pl.srm.registrationapi.registration.util.RegistrationCodeGenerator;

import java.time.LocalDateTime;

@Service
public class RegistrationPersistenceService {
    private static final String PERSON_CONSTRAINT = "uk_registration_turnus_pesel";
    private final RegistrationRepository repository;
    private final RegistrationCodeGenerator codeGenerator;
    private final EntityManager entityManager;
    private final TransactionTemplate transaction;

    public RegistrationPersistenceService(RegistrationRepository repository,
                                          RegistrationCodeGenerator codeGenerator,
                                          EntityManager entityManager,
                                          PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.entityManager = entityManager;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public String saveParticipant(RegistrationContext context, String payload) {
        return save(context, payload, RegistrationType.PARTICIPANT);
    }

    public String saveStaff(RegistrationContext context, String payload) {
        return save(context, payload, RegistrationType.STAFF);
    }

    private String save(RegistrationContext context, String payload, RegistrationType type) {
        for (int attempt = 0; ; attempt++) {
            try {
                return saveOnce(context, payload, type);
            } catch (RuntimeException exception) {
                boolean deadlock = false;
                for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                    if (cause instanceof java.sql.SQLException sql && sql.getErrorCode() == 1213) {
                        deadlock = true;
                    }
                }
                // Retry only a DB-confirmed deadlock, after rollback, never an uncertain commit.
                if (!deadlock || attempt >= 4) throw exception;
                try {
                    Thread.sleep(java.util.concurrent.ThreadLocalRandom.current().nextLong(10, 41) * (attempt + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw exception;
                }
            }
        }
    }

    private String saveOnce(RegistrationContext context, String payload, RegistrationType type) {
        try {
            // The upsert locks this group until commit; SELECT uses the same transaction.
            // Rollback undoes both the allocation and the registration insert.
            return transaction.execute(status -> {
                entityManager.createNativeQuery("""
                        INSERT INTO registration_counter (turnus_code, registration_type, last_number)
                        VALUES (:turnus, :type, 1)
                        ON DUPLICATE KEY UPDATE last_number = last_number + 1
                        """)
                        .setParameter("turnus", context.turnusCode())
                        .setParameter("type", type.name()).executeUpdate();
                long sequence = ((Number) entityManager.createNativeQuery("""
                        SELECT last_number FROM registration_counter
                        WHERE turnus_code = :turnus AND registration_type = :type
                        """)
                        .setParameter("turnus", context.turnusCode())
                        .setParameter("type", type.name()).getSingleResult()).longValue();
                String code = type == RegistrationType.PARTICIPANT
                        ? codeGenerator.generateParticipantCode(context.turnusCode(), sequence)
                        : codeGenerator.generateStaffCode(context.turnusCode(), sequence);
                repository.saveAndFlush(new Registration(code, type.name(), context.turnusCode(),
                        context.key(), context.isMinor(), RegistrationStatus.NEW.name(), null,
                        payload, LocalDateTime.now(), null));
                return code;
            });
        } catch (RuntimeException exception) {
            // Translate only this named constraint, after the transaction has rolled back.
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && PERSON_CONSTRAINT.equals(violation.getConstraintName())) {
                    throw new RegistrationException("ALREADY_REGISTERED",
                            "Ta osoba jest już zarejestrowana na ten turnus.");
                }
            }
            throw exception;
        }
    }
}
