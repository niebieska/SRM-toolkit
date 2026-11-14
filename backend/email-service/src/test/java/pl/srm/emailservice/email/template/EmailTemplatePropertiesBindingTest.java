package pl.srm.emailservice.email.template;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "EMAIL_INFO_URL=https://test.example/info",
        "EMAIL_CONTACT_EMAIL=test@example.test",
        "BIURO_NAME=Test Biuro",
        "BIURO_CONTACT_PHONE=+48 111 222 333",
        "PRIEST_NAME=Test Priest",
        "EMAIL_PRIEST_PHONE=+48 222 333 444",
        "EMAIL_WEBSITE_URL=https://test.example/website",
        "EMAIL_WEBSITE_LABEL=Test website",
        "EMAIL_FACEBOOK_URL=https://test.example/facebook",
        "EMAIL_FACEBOOK_LABEL=Test Facebook",
        "EMAIL_INSTAGRAM_URL=https://test.example/instagram",
        "EMAIL_INSTAGRAM_LABEL=Test Instagram",
        "PARTICIPANT_CARD_ADULT_URL=https://test.example/adult-card.pdf",
        "PARTICIPANT_CARD_ADULT_ENABLED=false",
        "EMAIL_SERVICE_PASSWORD=test-only-email-service-password",
        "PARTICIPANT_BANK_ACCOUNT=11 2222 3333 4444 5555 6666 7777",
        "PARTICIPANT_DEPOSIT_AMOUNT=500zł"
})
class EmailTemplatePropertiesBindingTest {
    @Autowired
    private EmailTemplateProperties properties;

    @Test
    void bindsTemplateSettingsAndParticipantCardDefaultsFromApplicationConfiguration() {
        assertEquals("https://test.example/info", properties.infoUrl());
        assertEquals("test@example.test", properties.contactEmail());
        assertEquals("Test Biuro", properties.biuroName());
        assertEquals("+48 111 222 333", properties.contactPhone());
        assertEquals("Test Priest", properties.priestName());
        assertEquals("+48 222 333 444", properties.priestPhone());
        assertEquals("https://test.example/website", properties.websiteUrl());
        assertEquals("Test website", properties.websiteLabel());
        assertEquals("https://test.example/facebook", properties.facebookUrl());
        assertEquals("Test Facebook", properties.facebookLabel());
        assertEquals("https://test.example/instagram", properties.instagramUrl());
        assertEquals("Test Instagram", properties.instagramLabel());
        assertEquals("https://test.example/adult-card.pdf", properties.participantCardAdultUrl());
        assertEquals(false, properties.participantCardAdultEnabled());
        assertEquals("https://rekolekcje.scj.pl/beta/wp-content/uploads/karta2020.pdf",
                properties.participantCardMinorUrl());
        assertEquals("11 2222 3333 4444 5555 6666 7777", properties.participantBankAccount());
        assertEquals("500zł", properties.participantDepositAmount());
    }
}
