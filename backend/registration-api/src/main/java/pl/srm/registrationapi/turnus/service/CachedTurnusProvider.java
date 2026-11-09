package pl.srm.registrationapi.turnus.service;

import pl.srm.registrationapi.registration.model.RegistrationType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.turnus.model.Turnus;
import java.util.List;

@Component
public class CachedTurnusProvider implements TurnusProvider {
    private static final Logger log = LoggerFactory.getLogger(CachedTurnusProvider.class);
    private final BiuroTurnusClient client;
    private volatile List<Turnus> cache;
    private boolean refreshPending = true;

    public CachedTurnusProvider(BiuroTurnusClient client) {
        this.client = client;
    }

    // Retries the initial load and failed scheduled refreshes without blocking catalogue reads.
    @Scheduled(fixedDelayString = "${turnus.catalogue.retry-interval}")
    public synchronized void retryPendingRefresh() {
        if (refreshPending) refreshCatalogue();
    }

    // Biuro changes are picked up at the start of each configured local hour.
    @Scheduled(cron = "${turnus.catalogue.refresh-cron}", zone = "${turnus.catalogue.time-zone}")
    public synchronized void refreshAtFullHour() {
        refreshPending = true;
        refreshCatalogue();
    }

    private void refreshCatalogue() {
        try {
            List<Turnus> replacement = List.copyOf(client.fetch());
            cache = replacement;
            refreshPending = false;
            log.info("Turnus catalogue refreshed: {} definitions", replacement.size());
        } catch (Exception exception) {
            // Do not log HTTP response bodies, URLs or credentials.
            refreshPending = true;
            log.warn("Turnus catalogue refresh failed ({}); {}", exception.getClass().getSimpleName(),
                    cache == null ? "catalogue unavailable" : "retaining previous catalogue");
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
