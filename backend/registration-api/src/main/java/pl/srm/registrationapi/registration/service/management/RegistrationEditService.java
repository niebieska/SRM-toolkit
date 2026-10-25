package pl.srm.registrationapi.registration.service.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.srm.registrationapi.registration.dto.request.RegistrationEditRequest;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.model.Registration;
import pl.srm.registrationapi.registration.model.RegistrationEditHistory;
import pl.srm.registrationapi.registration.repository.RegistrationEditHistoryRepository;
import pl.srm.registrationapi.registration.repository.RegistrationRepository;

import java.time.LocalDateTime;

@Service
public class RegistrationEditService {
    private final RegistrationRepository registrationRepository;
    private final RegistrationEditHistoryRepository historyRepository;
    private final RegistrationPayloadEditor payloadEditor;
    private final ObjectMapper objectMapper;

    public RegistrationEditService(RegistrationRepository registrationRepository,
                                   RegistrationEditHistoryRepository historyRepository,
                                   RegistrationPayloadEditor payloadEditor,
                                   ObjectMapper objectMapper) {
        this.registrationRepository = registrationRepository;
        this.historyRepository = historyRepository;
        this.payloadEditor = payloadEditor;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void update(String code, RegistrationEditRequest request) {
        Registration registration = registrationRepository.findByRegistrationCodeForUpdate(code)
                .orElseThrow(() -> new RegistrationException("REGISTRATION_NOT_FOUND", "Nie znaleziono zgłoszenia o podanym kodzie."));
        if (registration.getVersion() != request.expectedVersion()) {
            throw new RegistrationException("EDIT_CONFLICT", "Zgłoszenie zostało wcześniej zmienione. Odśwież szczegóły i spróbuj ponownie.");
        }

        RegistrationPayloadEditor.EditResult edit = payloadEditor.apply(registration, request.changes());
        if (!edit.hasChanges()) return;

        try {
            LocalDateTime changedAt = LocalDateTime.now();
            registration.setPayload(edit.payloadJson());
            registration.setUpdatedAt(changedAt);
            Registration saved = registrationRepository.saveAndFlush(registration);
            historyRepository.save(new RegistrationEditHistory(saved.getId(), changedAt,
                    request.actorAccountId().trim(), request.actorUsername().trim(),
                    objectMapper.writeValueAsString(edit.changedFields())));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not persist registration edit", ex);
        }
    }
}
