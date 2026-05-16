package pl.srm.registrationapi.turnus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.turnus.domain.Turnus;

import java.io.InputStream;
import java.util.List;

@Component
public class StubTurnusProvider implements TurnusProvider {

    private final ObjectMapper objectMapper;
    private List<Turnus> cache;

    public StubTurnusProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() throws Exception {
        InputStream is = new ClassPathResource("data/turnuses.json").getInputStream();
        TurnusListWrapper wrapper = objectMapper.readValue(is, TurnusListWrapper.class);
        cache = wrapper.getTurnusList();
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
                .orElseThrow(() -> new RegistrationException("TURNUS_NOT_FOUND", "Nie znaleziono wskazanego turnusu."));
    }
}
