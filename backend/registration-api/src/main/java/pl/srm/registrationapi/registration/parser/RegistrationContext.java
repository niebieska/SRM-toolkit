package pl.srm.registrationapi.registration.parser;

public record RegistrationContext(
        String turnusCode,
        String pesel,
        String key,
        boolean isMinor,
        boolean hasGuardian,
        boolean hasConsent1
) {
}
