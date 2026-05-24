package pl.srm.registrationapi.registration.util;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;

@Component
public class PeselHelper {

    private static final int[] PESEL_WEIGHTS = {1, 3, 7, 9, 1, 3, 7, 9, 1, 3, 1};

    public int calculateAge(String pesel) {
        return calculateAge(pesel, LocalDate.now());
    }

    public int calculateAge(String pesel, LocalDate referenceDate) {
        return Period.between(extractBirthDate(pesel), referenceDate).getYears();
    }

    public boolean isMinor(String pesel, LocalDate referenceDate) {
        if (!canExtractBirthDate(pesel)) {
            return false;
        }
        return calculateAge(pesel, referenceDate) < 18;
    }

    public boolean isValid(String pesel) {
        if (pesel == null || !pesel.matches("\\d{11}")) {
            return false;
        }

        if (!canExtractBirthDate(pesel)) {
            return false;
        }

        int sum = 0;
        for (int i = 0; i < PESEL_WEIGHTS.length; i++) {
            sum += Character.getNumericValue(pesel.charAt(i)) * PESEL_WEIGHTS[i];
        }
        return sum % 10 == 0;
    }

    private boolean canExtractBirthDate(String pesel) {
        try {
            extractBirthDate(pesel);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private LocalDate extractBirthDate(String pesel) {
        if (pesel == null || pesel.length() < 6) {
            throw new IllegalArgumentException("PESEL musi mieć 11 cyfr.");
        }

        int year = Integer.parseInt(pesel.substring(0, 2));
        int month = Integer.parseInt(pesel.substring(2, 4));
        int day = Integer.parseInt(pesel.substring(4, 6));

        int century;
        if (month >= 1 && month <= 12) {
            century = 1900;
        } else if (month >= 21 && month <= 32) {
            century = 2000;
            month -= 20;
        } else if (month >= 41 && month <= 52) {
            century = 2100;
            month -= 40;
        } else if (month >= 61 && month <= 72) {
            century = 2200;
            month -= 60;
        } else if (month >= 81 && month <= 92) {
            century = 1800;
            month -= 80;
        } else {
            throw new IllegalArgumentException("Nieprawidłowy miesiąc w numerze PESEL.");
        }

        return LocalDate.of(century + year, month, day);
    }
}
