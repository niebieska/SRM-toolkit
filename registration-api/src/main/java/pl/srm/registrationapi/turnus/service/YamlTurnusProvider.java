package pl.srm.registrationapi.turnus.service;

import org.springframework.stereotype.Component;
import pl.srm.registrationapi.common.exception.TurnusNotFoundException;
import pl.srm.registrationapi.turnus.config.TurnusProperties;
import pl.srm.registrationapi.turnus.domain.Turnus;

import java.util.List;

@Component
public class YamlTurnusProvider implements TurnusProvider {

    private final List<Turnus> cache;

    public YamlTurnusProvider(TurnusProperties properties) {
        this.cache = properties.getTurnuses().stream()
                .map(TurnusProperties.TurnusEntry::toDomain)
                .toList();
    }

    @Override
    public List<Turnus> getAvailableTurnuses() {
        return cache.stream()
                .filter(Turnus::active)
                .filter(Turnus::registrationOpen)
                .toList();
    }

    @Override
    public Turnus getByCode(String code) {
        return cache.stream()
                .filter(t -> t.turnusCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new TurnusNotFoundException("TURNUS_NOT_FOUND"));
    }
}
