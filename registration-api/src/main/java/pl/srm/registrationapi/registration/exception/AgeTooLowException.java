package pl.srm.registrationapi.registration.exception;

public class AgeTooLowException extends RuntimeException {

    public AgeTooLowException() {
        super("AGE_TOO_LOW");
    }
}
