package pl.srm.registrationapi.registration.service.management;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.srm.registrationapi.registration.dto.response.TurnusListPerson;
import pl.srm.registrationapi.registration.dto.response.TurnusListPerson.Address;
import pl.srm.registrationapi.registration.model.Registration;
import pl.srm.registrationapi.registration.repository.RegistrationRepository;
import pl.srm.registrationapi.registration.util.PeselHelper;

@Service
public class TurnusListService {
    private static final Logger log = LoggerFactory.getLogger(TurnusListService.class);
    private final RegistrationRepository repository;
    private final ObjectMapper mapper;
    private final PeselHelper pesel;

    public TurnusListService(RegistrationRepository repository, ObjectMapper mapper, PeselHelper pesel) {
        this.repository = repository;
        this.mapper = mapper;
        this.pesel = pesel;
    }

    public List<TurnusListPerson> accepted(String turnusCode) {
        return repository.findByTurnusCodeAndStatus(turnusCode, "ACCEPTED").stream().map(this::person).toList();
    }

    private TurnusListPerson person(Registration registration) {
        JsonNode root;
        try {
            root = mapper.readTree(registration.getPayload());
            if (root == null || !root.isObject()) return incomplete(registration);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            // Log only the identifier: the payload includes personal and health data.
            log.warn("Cannot read list data for registration {}", registration.getRegistrationCode());
            return incomplete(registration);
        }
        JsonNode person = root.path("person");
        JsonNode address = root.path("address");
        String identifier = text(person.path("pesel"));
        LocalDate birthDate = pesel.isValid(identifier) ? pesel.birthDate(identifier) : null;
        boolean staff = "STAFF".equals(registration.getRegistrationType());
        String firstName = text(person.path("firstName"));
        String lastName = text(person.path("lastName"));
        return new TurnusListPerson(registration.getRegistrationCode(), registration.getRegistrationType(),
                firstName, lastName, identifier,
                new Address(text(address.path("street")), text(address.path("houseNumber")),
                        text(address.path("postalCode")), text(address.path("city"))),
                birthDate, text(person.path("gender")), text(person.path("contact").path("phone")),
                text(root.path("guardian").path("contact").path("phone")), text(root.path("ice").path("phone")),
                staff ? text(root.path("role")) : null, staff ? text(root.path("subrole")) : null,
                birthDate == null || firstName == null || lastName == null, registration.isMinor());
    }

    private TurnusListPerson incomplete(Registration registration) {
        return new TurnusListPerson(registration.getRegistrationCode(), registration.getRegistrationType(),
                null, null, null, new Address(null, null, null, null), null, null,
                null, null, null, null, null, true, registration.isMinor());
    }

    private String text(JsonNode node) {
        String value = node.textValue();
        return value == null || value.isBlank() ? null : value.trim();
    }
}
