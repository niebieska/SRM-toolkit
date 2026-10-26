package pl.srm.emailservice.email.template;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "EMAIL_REGULATIONS_URL=https://test.example/rules",
        "EMAIL_INFO_URL=https://test.example/info",
        "EMAIL_CONTACT_EMAIL=test@example.test",
        "EMAIL_CONTACT_PHONE=+48 111 222 333",
        "EMAIL_LOGO_URL=https://test.example/logo.png",
        "PARTICIPANT_BANK_ACCOUNT=11 2222 3333 4444 5555 6666 7777",
        "PARTICIPANT_DEPOSIT_AMOUNT=500zł"
})
class EmailTemplatePropertiesBindingTest {
    @Autowired
    private EmailTemplateProperties properties;

    @Test
    void bindsTemplateSettingsAndParticipantCardDefaultsFromApplicationConfiguration() {
        assertEquals("https://test.example/rules", properties.regulationsUrl());
        assertEquals("https://test.example/info", properties.infoUrl());
        assertEquals("test@example.test", properties.contactEmail());
        assertEquals("+48 111 222 333", properties.contactPhone());
        assertEquals("https://test.example/logo.png", properties.logoUrl());
        assertEquals("https://rekolekcje.scj.pl/beta/wp-content/uploads/karta2020.pdf",
                properties.participantCardAdultUrl());
        assertEquals(properties.participantCardAdultUrl(), properties.participantCardMinorUrl());
        assertEquals("11 2222 3333 4444 5555 6666 7777", properties.participantBankAccount());
        assertEquals("500zł", properties.participantDepositAmount());
    }
}
