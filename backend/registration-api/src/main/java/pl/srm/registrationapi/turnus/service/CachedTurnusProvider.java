package pl.srm.registrationapi.turnus.service;

import pl.srm.registrationapi.registration.model.RegistrationType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.turnus.model.Turnus;
import java.time.Duration;
import java.util.List;
import java.util.function.LongSupplier;

@Component
public class CachedTurnusProvider implements TurnusProvider {
    private static final Logger log = LoggerFactory.getLogger(CachedTurnusProvider.class);
    private final BiuroTurnusClient client;
    private final long refreshNanos;
    private final LongSupplier time;
    private volatile List<Turnus> cache;
    private long lastAttempt;
    private boolean attempted;

    @org.springframework.beans.factory.annotation.Autowired
    public CachedTurnusProvider(BiuroTurnusClient client,
            @Value("${turnus.catalogue.refresh-interval}") Duration refreshInterval) {
        this(client, refreshInterval, System::nanoTime);
    }

    CachedTurnusProvider(BiuroTurnusClient client, Duration refreshInterval, LongSupplier time) {
        if (refreshInterval.isNegative() || refreshInterval.isZero())
            throw new IllegalArgumentException("Turnus refresh interval must be positive");
        this.client = client;
        this.refreshNanos = refreshInterval.toNanos();
        this.time = time;
    }

    // The first fetch runs asynchronously after startup. Reads never wait for network I/O.
    @Scheduled(fixedDelayString = "${turnus.catalogue.retry-interval}")
    public synchronized void refreshIfDue() {
        long now = time.getAsLong();
        if (cache != null && attempted && now - lastAttempt < refreshNanos) return;
        try {
            List<Turnus> replacement = List.copyOf(client.fetch());
            cache = replacement;
            log.info("Turnus catalogue refreshed: {} definitions", replacement.size());
        } catch (Exception exception) {
            // Do not log HTTP response bodies, URLs or credentials.
            log.warn("Turnus catalogue refresh failed ({}); {}", exception.getClass().getSimpleName(),
                    cache == null ? "catalogue unavailable" : "retaining previous catalogue");
        } finally {
            lastAttempt = time.getAsLong();
            attempted = true;
        }
    }

    private List<Turnus> snapshot() {
        List<Turnus> snapshot = cache;
        if (snapshot == null) throw new RegistrationException("TURNUS_CATALOGUE_UNAVAILABLE",
                "Lista turnusów jest chwilowo niedostępna. Spróbuj ponownie później.");
        return snapshot;
    }

    @Override public List<Turnus> getAvailableTurnuses() {
        return getAvailableTurnuses(RegistrationType.PARTICIPANT);
    }

    @Override public List<Turnus> getAvailableTurnuses(RegistrationType type) {
        return snapshot().stream().filter(Turnus::active)
                .filter(t -> type == RegistrationType.STAFF
                        ? t.staffRegistrationOpen() : t.participantRegistrationOpen()).toList();
    }

    @Override public Turnus getByCode(String code) {
        return snapshot().stream().filter(turnus -> turnus.turnusCode().equals(code)).findFirst()
                .orElseThrow(() -> new RegistrationException("TURNUS_NOT_FOUND", "Nie znaleziono wskazanego turnusu."));
    }
}
