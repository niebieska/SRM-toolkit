package pl.srm.registrationapi.turnus.service;

import org.junit.jupiter.api.Test;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.validator.TurnusValidator;
import pl.srm.registrationapi.turnus.model.Turnus;
import pl.srm.registrationapi.turnus.model.SeasonType;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CachedTurnusProviderTest {
    final BiuroTurnusClient client = mock(BiuroTurnusClient.class);
    final AtomicLong time = new AtomicLong();
    final CachedTurnusProvider provider = new CachedTurnusProvider(client, Duration.ofHours(1), time::get);

    Turnus turnus(String code, boolean active, boolean open) {
        return new Turnus(code, "Turnus", null, "Miejsce", SeasonType.SUMMER, 2026,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10), 14, 60, active, open);
    }

    @Test void unavailableUntilFirstSuccessThenRecoversOnRetry() throws Exception {
        when(client.fetch()).thenThrow(new IllegalStateException("offline"))
                .thenReturn(List.of(turnus("T1", true, true)));
        provider.refreshIfDue();
        assertEquals("TURNUS_CATALOGUE_UNAVAILABLE",
                assertThrows(RegistrationException.class, provider::getAvailableTurnuses).getCode());
        assertEquals("TURNUS_CATALOGUE_UNAVAILABLE",
                assertThrows(RegistrationException.class, () -> provider.getByCode("T1")).getCode());
        time.addAndGet(Duration.ofSeconds(30).toNanos());
        provider.refreshIfDue();
        assertEquals("T1", provider.getByCode("T1").turnusCode());
    }

    @Test void replacesWholeSnapshotHourlyAndPreservesPreviousSnapshotDuringOutage() throws Exception {
        Turnus first = turnus("T1", true, true);
        when(client.fetch()).thenReturn(List.of(first))
                .thenThrow(new IllegalStateException("offline"))
                .thenReturn(List.of(turnus("T2", true, true)));
        provider.refreshIfDue();
        provider.refreshIfDue();
        verify(client, times(1)).fetch();
        time.addAndGet(Duration.ofHours(1).toNanos());
        provider.refreshIfDue();
        assertEquals(List.of(first), provider.getAvailableTurnuses());
        provider.refreshIfDue();
        verify(client, times(2)).fetch();
        time.addAndGet(Duration.ofHours(24).toNanos());
        assertEquals(first, provider.getByCode("T1")); // No expiry cutoff.
        provider.refreshIfDue();
        assertEquals("T2", provider.getAvailableTurnuses().getFirst().turnusCode());
        assertEquals("TURNUS_NOT_FOUND", assertThrows(RegistrationException.class,
                () -> provider.getByCode("T1")).getCode());
    }

    @Test void filtersFormOptionsButRetainsClosedAndInactiveDefinitionsForSubmissionValidation() throws Exception {
        when(client.fetch()).thenReturn(List.of(turnus("OPEN", true, true),
                turnus("CLOSED", true, false), turnus("ARCHIVED", false, false)));
        provider.refreshIfDue();
        assertEquals(List.of("OPEN"), provider.getAvailableTurnuses().stream().map(Turnus::turnusCode).toList());
        var validator = new TurnusValidator();
        assertEquals("REGISTRATION_CLOSED", assertThrows(RegistrationException.class,
                () -> validator.validate(provider.getByCode("CLOSED"))).getCode());
        assertEquals("TURNUS_INACTIVE", assertThrows(RegistrationException.class,
                () -> validator.validate(provider.getByCode("ARCHIVED"))).getCode());
    }

    @Test void successfulEmptyCatalogueIsAvailableAndDoesNotRetryEveryThirtySeconds() throws Exception {
        when(client.fetch()).thenReturn(List.of());
        provider.refreshIfDue();
        assertEquals(List.of(), provider.getAvailableTurnuses());
        assertEquals("TURNUS_NOT_FOUND", assertThrows(RegistrationException.class,
                () -> provider.getByCode("T1")).getCode());
        time.addAndGet(Duration.ofSeconds(30).toNanos());
        provider.refreshIfDue();
        verify(client, times(1)).fetch();
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
        provider.refreshIfDue();
        time.addAndGet(Duration.ofHours(1).toNanos());
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var refresh = executor.submit(provider::refreshIfDue);
            try {
                assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
                assertEquals(List.of(old), provider.getAvailableTurnuses());
            } finally { release.countDown(); }
            refresh.get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertEquals("NEW", provider.getAvailableTurnuses().getFirst().turnusCode());
    }
}
