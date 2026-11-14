package pl.srm.emailservice.email.template;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateRendererTest {
    private static final String ADULT_URL = "https://cards.example.test/adult.pdf";
    private static final String MINOR_URL = "https://cards.example.test/minor.pdf";

    private final TemplateRenderer renderer = new TemplateRenderer(templateEngine(), new DefaultResourceLoader(),
            new EmailTemplateProperties("https://example.test/info", "contact@example.test",
                    "Example Office", "+48 123 456 789", "Example Priest", "+48 987 654 321",
                    "https://example.test/", "example.test", "https://facebook.example.test/", "facebook.example.test",
                    "https://instagram.example.test/", "instagram.example.test", ADULT_URL, false, MINOR_URL,
                    "11 1111 1111 1111 1111 1111 1111", "500 zł"));

    @Test
    void acceptedAdultDoesNotReceiveParticipantCard() {
        String html = renderer.render("status-update", Map.of(
                "status", "ACCEPTED", "registrationType", "PARTICIPANT",
                "includeParticipantCard", "true", "isMinor", "false",
                "participantCardAdultEnabled", "true",
                "participantCardAdultUrl", "https://attacker.example.test/fake.pdf"));

        assertFalse(html.contains("Pobierz kartę uczestnictwa (PDF)"));
        assertFalse(html.contains("attacker.example.test"));
        assertTrue(html.contains("<p>Dzień dobry,</p>"));
        assertFalse(html.contains("Dzień dobry, <strong"));
        assertTrue(html.contains("11 1111 1111 1111 1111 1111 1111"));
        assertTrue(html.contains("500 zł"));
    }

    @Test
    void acceptedMinorRendersMinorCardLink() {
        String html = renderer.render("status-update", Map.of(
                "status", "ACCEPTED", "registrationType", "PARTICIPANT",
                "includeParticipantCard", "true", "isMinor", "true"));

        assertTrue(html.contains("href=\"" + MINOR_URL + "\""));
    }

    @Test
    void cardButtonIsHiddenForUnacceptedStatusOrUnsupportedRegistrationType() {
        String rejected = renderer.render("status-update", Map.of(
                "status", "REJECTED", "registrationType", "PARTICIPANT",
                "includeParticipantCard", "true", "isMinor", "false"));
        String adultStaff = renderer.render("status-update", Map.of(
                "status", "ACCEPTED", "registrationType", "STAFF",
                "includeParticipantCard", "false", "isMinor", "false"));

        assertFalse(rejected.contains("Pobierz kartę uczestnictwa (PDF)"));
        assertFalse(adultStaff.contains("Pobierz kartę uczestnictwa (PDF)"));
        assertTrue(adultStaff.contains("Dziękujemy za gotowość do przyjazdu na turnus."));
        assertTrue(adultStaff.contains("Jeśli przyjazd nie będzie możliwy, prosimy o jak najszybszą informację."));
        assertFalse(adultStaff.contains("Dane do przelewu"));
    }

    @Test
    void allTemplatesRenderConfiguredFooterBrandingAndLinks() {
        for (String template : new String[]{"registration-confirmation-participant", "registration-confirmation-staff", "status-update"}) {
            String html = renderer.render(template, Map.of());

            assertTrue(html.contains("Example Office"));
            assertTrue(html.contains("Example Priest"));
            assertTrue(html.contains("contact@example.test"));
            assertTrue(html.contains("+48 123 456 789"));
            assertTrue(html.contains("+48 987 654 321"));
            assertTrue(html.contains("href=\"https://example.test/\""));
            assertTrue(html.contains("example.test</span>"));
            assertTrue(html.contains("href=\"https://facebook.example.test/\""));
            assertTrue(html.contains("facebook.example.test</span>"));
            assertTrue(html.contains("white-space: nowrap;"));
            assertTrue(html.contains("href=\"https://instagram.example.test/\""));
            assertTrue(html.contains("instagram.example.test</span>"));
        }
    }

    @Test
    void acceptedMinorStaffGetsCardButNoParticipantTransferInstructions() {
        String html = renderer.render("status-update", Map.of(
                "status", "ACCEPTED", "registrationType", "STAFF",
                "includeParticipantCard", "true", "isMinor", "true"));

        assertTrue(html.contains("href=\"" + MINOR_URL + "\""));
        assertFalse(html.contains("Dane do przelewu"));
    }

    @Test
    void acceptedParticipantWithoutPaymentConfigurationDoesNotShowPlaceholderDetails() {
        TemplateRenderer rendererWithoutPayment = new TemplateRenderer(templateEngine(), new DefaultResourceLoader(),
                new EmailTemplateProperties("https://example.test/info", "contact@example.test",
                        "Example Office", "+48 123 456 789", "Example Priest", "+48 987 654 321",
                        "https://example.test/", "example.test", "https://facebook.example.test/", "facebook.example.test",
                        "https://instagram.example.test/", "instagram.example.test", ADULT_URL, false, MINOR_URL,
                        "", ""));

        String html = rendererWithoutPayment.render("status-update", Map.of(
                "status", "ACCEPTED", "registrationType", "PARTICIPANT",
                "includeParticipantCard", "false", "isMinor", "false"));

        assertTrue(html.contains("Szczegóły dotyczące wpłaty zaliczki przekażemy osobno."));
        assertFalse(html.contains("Dane do przelewu"));
        assertFalse(html.contains("11 1111 1111 1111 1111 1111 1111"));
        assertFalse(html.contains("500 zł"));
    }

    private static SpringTemplateEngine templateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
