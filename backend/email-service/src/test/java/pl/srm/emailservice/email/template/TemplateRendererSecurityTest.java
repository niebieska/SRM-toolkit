package pl.srm.emailservice.email.template;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TemplateRendererSecurityTest {
    private final EmailTemplateProperties properties = new EmailTemplateProperties(
            "https://trusted.example.test/info", "contact@example.test", "Trusted Office",
            "+48555000111", "Trusted Priest", "+48555000222", "https://trusted.example.test/",
            "trusted.example.test", "https://trusted.example.test/facebook", "Trusted Facebook",
            "https://trusted.example.test/instagram", "Trusted Instagram",
            "https://trusted.example.test/adult-card.pdf", false,
            "https://trusted.example.test/minor-card.pdf",
            "11 2222 3333 4444 5555 6666 7777", "500zł");

    @Test
    void callerCannotReplaceTrustedLinkOrContactVariables() {
        var engine = mock(SpringTemplateEngine.class);
        var loader = mock(ResourceLoader.class);
        var resource = mock(Resource.class);
        when(loader.getResource("classpath:templates/status-update.html")).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        var renderer = new TemplateRenderer(engine, loader, properties);
        renderer.render("status-update", Map.ofEntries(
                Map.entry("infoUrl", "untrusted"), Map.entry("biuroName", "untrusted"),
                Map.entry("contactEmail", "untrusted"), Map.entry("contactPhone", "untrusted"),
                Map.entry("priestName", "untrusted"), Map.entry("priestPhone", "untrusted"),
                Map.entry("websiteUrl", "untrusted"), Map.entry("websiteLabel", "untrusted"),
                Map.entry("facebookUrl", "untrusted"), Map.entry("facebookLabel", "untrusted"),
                Map.entry("instagramUrl", "untrusted"), Map.entry("instagramLabel", "untrusted"),
                Map.entry("participantCardAdultUrl", "untrusted"), Map.entry("participantCardAdultEnabled", "true"),
                Map.entry("participantBankAccount", "untrusted"), Map.entry("participantDepositAmount", "untrusted"),
                Map.entry("participantPaymentInstructionsConfigured", "false"),
                Map.entry("registeredName", "Synthetic participant")));
        var context = ArgumentCaptor.forClass(Context.class);
        verify(engine).process(eq("status-update"), context.capture());
        assertEquals(properties.infoUrl(), context.getValue().getVariable("infoUrl"));
        assertEquals(properties.biuroName(), context.getValue().getVariable("biuroName"));
        assertEquals(properties.contactEmail(), context.getValue().getVariable("contactEmail"));
        assertEquals(properties.contactPhone(), context.getValue().getVariable("contactPhone"));
        assertEquals(properties.priestName(), context.getValue().getVariable("priestName"));
        assertEquals(properties.priestPhone(), context.getValue().getVariable("priestPhone"));
        assertEquals(properties.websiteUrl(), context.getValue().getVariable("websiteUrl"));
        assertEquals(properties.websiteLabel(), context.getValue().getVariable("websiteLabel"));
        assertEquals(properties.facebookUrl(), context.getValue().getVariable("facebookUrl"));
        assertEquals(properties.facebookLabel(), context.getValue().getVariable("facebookLabel"));
        assertEquals(properties.instagramUrl(), context.getValue().getVariable("instagramUrl"));
        assertEquals(properties.instagramLabel(), context.getValue().getVariable("instagramLabel"));
        assertEquals(properties.participantCardAdultUrl(), context.getValue().getVariable("participantCardAdultUrl"));
        assertEquals(properties.participantCardAdultEnabled(), context.getValue().getVariable("participantCardAdultEnabled"));
        assertEquals(properties.participantBankAccount(), context.getValue().getVariable("participantBankAccount"));
        assertEquals(properties.participantDepositAmount(), context.getValue().getVariable("participantDepositAmount"));
        assertEquals(true, context.getValue().getVariable("participantPaymentInstructionsConfigured"));
        assertEquals("Synthetic participant", context.getValue().getVariable("registeredName"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"registration-confirmation-participant", "registration-confirmation-staff", "status-update"})
    void actualTemplatesEscapeCallerText(String template) {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var renderer = new TemplateRenderer(engine, new DefaultResourceLoader(), properties);
        String markup = "<script>alert(1)</script>";
        String html = renderer.render(template, Map.of("registeredName", markup,
                "recipientName", markup, "rejectionReason", markup, "status", "REJECTED"));
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }
}
