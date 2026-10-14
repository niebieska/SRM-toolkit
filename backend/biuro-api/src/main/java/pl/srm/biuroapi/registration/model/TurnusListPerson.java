package pl.srm.biuroapi.registration.model;

import java.time.LocalDate;

public record TurnusListPerson(String registrationCode, String registrationType,
        String firstName, String lastName, String pesel, Address address,
        LocalDate birthDate, String gender, String personPhone, String guardianPhone,
        String icePhone, String role, String subrole, boolean incompleteData) {
    public record Address(String street, String houseNumber, String postalCode, String city) {}
}
