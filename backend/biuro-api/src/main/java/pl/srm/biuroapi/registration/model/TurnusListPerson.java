package pl.srm.biuroapi.registration.model;

import java.time.LocalDate;
import java.util.List;

public record TurnusListPerson(String registrationCode, String registrationType,
        String firstName, String lastName, String pesel, Address address,
        LocalDate birthDate, String gender, String personPhone, String guardianPhone,
        String icePhone, String role, String subrole, boolean incompleteData, boolean minor, AdditionalData additionalData) {
    public record AdditionalData(String email, String contactFirstName, String contactLastName,
            String contactRelation, String contactRelationOther, String guardianEmail,
            List<Qualification> qualifications, Boolean imageConsent) {
        public AdditionalData { qualifications = qualifications == null ? List.of() : List.copyOf(qualifications); }
    }
    public record Qualification(String code, String detail) {}
    public record Address(String street, String houseNumber, String postalCode, String city) {}
}
