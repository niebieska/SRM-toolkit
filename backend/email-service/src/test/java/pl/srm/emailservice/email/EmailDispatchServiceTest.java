package pl.srm.emailservice.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.srm.emailservice.email.domain.EmailMessage;
import pl.srm.emailservice.email.domain.EmailSender;
import pl.srm.emailservice.email.service.EmailDispatchService;
import pl.srm.emailservice.email.template.TemplateRenderer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailDispatchServiceTest {

    @Mock
    private EmailSender emailSender;

    @Mock
    private TemplateRenderer templateRenderer;

    @InjectMocks
    private EmailDispatchService emailDispatchService;

    @Test
    void sendParticipantEmailCallsSenderWithCorrectRecipient() {
        when(templateRenderer.render(
                "registration-confirmation-participant",
                Map.of("registrationCode", "UCZ-2026-00042")
        )).thenReturn("<html>ok</html>");

        emailDispatchService.sendEmail(
                "jan.kowalski@example.com",
                "registration-confirmation-participant",
                Map.of("registrationCode", "UCZ-2026-00042")
        );

        ArgumentCaptor<EmailMessage> captor =
                ArgumentCaptor.forClass(EmailMessage.class);

        verify(emailSender).send(captor.capture());

        assertEquals(
                "jan.kowalski@example.com",
                captor.getValue().to()
        );

        assertEquals(
                "Potwierdzenie zgłoszenia uczestnika",
                captor.getValue().subject()
        );
    }

    @Test
    void staffConfirmationTemplateUsesStaffSubject() {
        when(templateRenderer.render(
                "registration-confirmation-staff",
                Map.of("registrationCode", "KAD-2026-00042")
        )).thenReturn("<html>ok</html>");

        emailDispatchService.sendEmail(
                "kadra@example.com",
                "registration-confirmation-staff",
                Map.of("registrationCode", "KAD-2026-00042")
        );

        ArgumentCaptor<EmailMessage> captor =
                ArgumentCaptor.forClass(EmailMessage.class);

        verify(emailSender).send(captor.capture());

        assertEquals(
                "Potwierdzenie zgłoszenia kadry",
                captor.getValue().subject()
        );
    }

    @Test
    void unknownTemplateThrowsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> emailDispatchService.sendEmail(
                        "jan.kowalski@example.com",
                        "unknown",
                        Map.of()
                )
        );
    }
}