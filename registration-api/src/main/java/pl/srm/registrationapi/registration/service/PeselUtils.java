package pl.srm.registrationapi.registration.service;

import org.springframework.stereotype.Component;

    @Component
    public class PeselUtils {

        public int calculateAge(String pesel) {

            int birthYear = extractBirthYear(pesel);

            int currentYear = 2026; // later → LocalDate.now()

            return currentYear - birthYear;
        }


        public String determineGender(String pesel) {

            int genderDigit = Character.getNumericValue(pesel.charAt(9));

            if (genderDigit % 2 == 0) {
                return "FEMALE";
            } else {
                return "MALE";
            }
        }

        private int extractBirthYear(String pesel) {

            int year = Integer.parseInt(pesel.substring(0, 2));
            int month = Integer.parseInt(pesel.substring(2, 4));

            if (month > 20) {
                year += 2000;
            } else {
                year += 1900;
            }

            return year;
        }
    }

