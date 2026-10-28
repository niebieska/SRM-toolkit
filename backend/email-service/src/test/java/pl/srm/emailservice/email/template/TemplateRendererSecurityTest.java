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
            "https://trusted.example.test/rules", "https://trusted.example.test/info",
            "contact@example.test", "+48555000111", "https://trusted.example.test/logo.png",
            "https://trusted.example.test/adult-card.pdf", "https://trusted.example.test/minor-card.pdf",
            "11 2222 3333 4444 5555 6666 7777", "500zł");

    @Test
    void callerCannotReplaceTrustedLinkOrContactVariables() {
        var engine = mock(SpringTemplateEngine.class);
        var loader = mock(ResourceLoader.class);
        var resource = mock(Resource.class);
        when(loader.getResource("classpath:templates/status-update.html")).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        var renderer = new TemplateRenderer(engine, loader, properties);
        renderer.render("status-update", Map.of("regulationsUrl", "untrusted", "infoUrl", "untrusted",
                "contactEmail", "untrusted", "contactPhone", "untrusted", "logoUrl", "untrusted",
                "participantBankAccount", "untrusted", "participantDepositAmount", "untrusted",
                "participantPaymentInstructionsConfigured", "false",
                "registeredName", "Synthetic participant"));
        var context = ArgumentCaptor.forClass(Context.class);
        verify(engine).process(eq("status-update"), context.capture());
        assertEquals(properties.regulationsUrl(), context.getValue().getVariable("regulationsUrl"));
        assertEquals(properties.infoUrl(), context.getValue().getVariable("infoUrl"));
        assertEquals(properties.contactEmail(), context.getValue().getVariable("contactEmail"));
        assertEquals(properties.contactPhone(), context.getValue().getVariable("contactPhone"));
        assertEquals(properties.logoUrl(), context.getValue().getVariable("logoUrl"));
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
