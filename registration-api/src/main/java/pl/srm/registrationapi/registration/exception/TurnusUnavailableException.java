package pl.srm.registrationapi.registration.exception;

public class TurnusUnavailableException extends RuntimeException {

    private final String code;

    public TurnusUnavailableException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}