package pl.srm.biuroapi.registration.model;

public record RegistrationDetail(
        String registrationCode,
        String registrationType,
        String turnusCode,
        boolean minor,
        String status,
        String rejectionReason,
        String createdAt,
        String firstName,
        String lastName,
        Integer age,
        Object payload
) {
}
