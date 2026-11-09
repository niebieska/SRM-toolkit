package pl.srm.registrationapi.turnus.service;

import org.junit.jupiter.api.Test;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.validator.TurnusValidator;
import pl.srm.registrationapi.turnus.model.Turnus;
import pl.srm.registrationapi.turnus.model.SeasonType;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CachedTurnusProviderTest {
    final BiuroTurnusClient client = mock(BiuroTurnusClient.class);
    final CachedTurnusProvider provider = new CachedTurnusProvider(client);

    Turnus turnus(String code, boolean active, boolean open) {
        return new Turnus(code, "Turnus", null, "Miejsce", SeasonType.SUMMER, 2026,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10), 14, 60, 10, active, open, open);
    }

    @Test void unavailableUntilFirstSuccessThenRecoversOnRetry() throws Exception {
        when(client.fetch()).thenThrow(new IllegalStateException("offline"))
                .thenReturn(List.of(turnus("T1", true, true)));
        provider.retryPendingRefresh();
        assertEquals("TURNUS_CATALOGUE_UNAVAILABLE",
                assertThrows(RegistrationException.class, provider::getAvailableTurnuses).getCode());
        assertEquals("TURNUS_CATALOGUE_UNAVAILABLE",
                assertThrows(RegistrationException.class, () -> provider.getByCode("T1")).getCode());
        provider.retryPendingRefresh();
        assertEquals("T1", provider.getByCode("T1").turnusCode());
        provider.retryPendingRefresh();
        verify(client, times(2)).fetch();
    }

    @Test void failedHourlyRefreshRetainsSnapshotAndRetriesUntilSuccess() throws Exception {
        Turnus first = turnus("T1", true, true);
        when(client.fetch()).thenReturn(List.of(first))
                .thenThrow(new IllegalStateException("offline"))
                .thenReturn(List.of(turnus("T2", true, true)));
        provider.retryPendingRefresh();
        provider.retryPendingRefresh();
        verify(client, times(1)).fetch();

        provider.refreshAtFullHour();
        assertEquals(List.of(first), provider.getAvailableTurnuses());
        provider.retryPendingRefresh();
        assertEquals("T2", provider.getAvailableTurnuses().getFirst().turnusCode());
        provider.retryPendingRefresh();
        verify(client, times(3)).fetch();
        assertEquals("TURNUS_NOT_FOUND", assertThrows(RegistrationException.class,
                () -> provider.getByCode("T1")).getCode());
    }

    @Test void filtersFormOptionsButRetainsClosedAndInactiveDefinitionsForSubmissionValidation() throws Exception {
        when(client.fetch()).thenReturn(List.of(turnus("OPEN", true, true),
                turnus("CLOSED", true, false), turnus("ARCHIVED", false, false)));
        provider.retryPendingRefresh();
        assertEquals(List.of("OPEN"), provider.getAvailableTurnuses().stream().map(Turnus::turnusCode).toList());
        var validator = new TurnusValidator();
        assertEquals("REGISTRATION_CLOSED", assertThrows(RegistrationException.class,
                () -> validator.validate(provider.getByCode("CLOSED"), pl.srm.registrationapi.registration.model.RegistrationType.PARTICIPANT)).getCode());
        assertEquals("TURNUS_INACTIVE", assertThrows(RegistrationException.class,
                () -> validator.validate(provider.getByCode("ARCHIVED"), pl.srm.registrationapi.registration.model.RegistrationType.PARTICIPANT)).getCode());
    }

    @Test void participantAndStaffOptionsAndValidationUseIndependentSwitches() throws Exception {
        var participant = turnus("PARTICIPANTS", true, true);
        var staff = new Turnus("STAFF", "Turnus", null, "Miejsce", SeasonType.SUMMER, 2026,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10), 14, 60, 10, true, false, true);
        participant = new Turnus(participant.turnusCode(), participant.turnusName(), null, participant.turnusLocation(),
                participant.seasonType(), participant.seasonYear(), participant.startDate(), participant.endDate(),
                14, 60, 10, true, true, false);
        when(client.fetch()).thenReturn(List.of(participant, staff, turnus("ARCHIVED", false, true)));
        provider.retryPendingRefresh();
        assertEquals(List.of("PARTICIPANTS"), provider.getAvailableTurnuses(
                pl.srm.registrationapi.registration.model.RegistrationType.PARTICIPANT).stream().map(Turnus::turnusCode).toList());
        assertEquals(List.of("STAFF"), provider.getAvailableTurnuses(
                pl.srm.registrationapi.registration.model.RegistrationType.STAFF).stream().map(Turnus::turnusCode).toList());
        var validator = new TurnusValidator();
        assertDoesNotThrow(() -> validator.validate(staff, pl.srm.registrationapi.registration.model.RegistrationType.STAFF));
        assertEquals("REGISTRATION_CLOSED", assertThrows(RegistrationException.class,
                () -> validator.validate(staff, pl.srm.registrationapi.registration.model.RegistrationType.PARTICIPANT)).getCode());
        assertEquals("REGISTRATION_CLOSED", assertThrows(RegistrationException.class,
                () -> validator.validate(provider.getByCode("PARTICIPANTS"), pl.srm.registrationapi.registration.model.RegistrationType.STAFF)).getCode());
    }

    @Test void successfulEmptyCatalogueIsAvailableAndDoesNotRetryEveryThirtySeconds() throws Exception {
        when(client.fetch()).thenReturn(List.of());
        provider.retryPendingRefresh();
        assertEquals(List.of(), provider.getAvailableTurnuses());
        assertEquals("TURNUS_NOT_FOUND", assertThrows(RegistrationException.class,
                () -> provider.getByCode("T1")).getCode());
        provider.retryPendingRefresh();
        verify(client, times(1)).fetch();

        provider.refreshAtFullHour();
        verify(client, times(2)).fetch();
    }

    @Test void readersKeepOldSnapshotWhileRefreshIsInFlight() throws Exception {
        Turnus old = turnus("OLD", true, true);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        when(client.fetch()).thenReturn(List.of(old)).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                throw new IllegalStateException("Refresh test timed out");
            return List.of(turnus("NEW", true, true));
        });
        provider.retryPendingRefresh();
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var refresh = executor.submit(provider::refreshAtFullHour);
            try {
                assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
                assertEquals(List.of(old), provider.getAvailableTurnuses());
            } finally { release.countDown(); }
            refresh.get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertEquals("NEW", provider.getAvailableTurnuses().getFirst().turnusCode());
    }
}
