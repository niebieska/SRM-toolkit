package pl.srm.registrationapi.registration.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import pl.srm.registrationapi.registration.parser.RegistrationContext;
import pl.srm.registrationapi.registration.repository.RegistrationRepository;
import pl.srm.registrationapi.registration.service.submission.RegistrationPersistenceService;
import pl.srm.registrationapi.registration.util.RegistrationCodeGenerator;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegistrationPersistenceRetryTest {
    private EntityManager entityManager;
    private PlatformTransactionManager manager;
    private RegistrationRepository repository;
    private Query query;
    private RegistrationPersistenceService service;
    private final RegistrationContext context = new RegistrationContext("A", "unused", "person", false, false, true);

    @BeforeEach
    void setup() {
        entityManager = mock(EntityManager.class);
        manager = mock(PlatformTransactionManager.class);
        repository = mock(RegistrationRepository.class);
        query = mock(Query.class, RETURNS_SELF);
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        service = new RegistrationPersistenceService(repository, new RegistrationCodeGenerator(), entityManager, manager);
    }

    private RuntimeException failure(int code) {
        return new PersistenceException(new SQLException("Simulated database failure", "40001", code));
    }

    @Test
    void retriesConfirmedDeadlockAfterRollbackAndCommitsOnce() {
        when(query.executeUpdate()).thenThrow(failure(1213)).thenReturn(1);
        when(query.getSingleResult()).thenReturn(1L);
        assertEquals("REG-P-A-1", service.saveParticipant(context, "{}"));
        var order = inOrder(manager);
        order.verify(manager).getTransaction(any());
        order.verify(manager).rollback(any());
        order.verify(manager).getTransaction(any());
        order.verify(manager).commit(any());
        verify(repository, times(1)).saveAndFlush(any());
    }

    @Test
    void stopsAfterFiveFailedAttempts() {
        var failure = failure(1213);
        when(query.executeUpdate()).thenThrow(failure);
        assertSame(failure, assertThrows(RuntimeException.class, () -> service.saveParticipant(context, "{}")));
        verify(manager, times(5)).rollback(any());
        verify(manager, never()).commit(any());
        verifyNoInteractions(repository);
    }

    @Test
    void doesNotRetryOtherDatabaseErrors() {
        var failure = failure(1205); // Lock timeout is not a confirmed transaction deadlock.
        when(query.executeUpdate()).thenThrow(failure);
        assertSame(failure, assertThrows(RuntimeException.class, () -> service.saveParticipant(context, "{}")));
        verify(query, times(1)).executeUpdate();
        verify(manager, times(1)).rollback(any());
        verifyNoInteractions(repository);
    }

    @Test
    void interruptedBackoffStopsRetriesAndPreservesInterruptFlag() {
        when(query.executeUpdate()).thenThrow(failure(1213));
        Thread.currentThread().interrupt();
        try {
            assertThrows(RuntimeException.class, () -> service.saveParticipant(context, "{}"));
            assertTrue(Thread.currentThread().isInterrupted());
            verify(query, times(1)).executeUpdate();
        } finally {
            Thread.interrupted();
        }
    }
}
