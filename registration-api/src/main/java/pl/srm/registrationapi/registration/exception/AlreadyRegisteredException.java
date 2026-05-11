
package pl.srm.registrationapi.registration.exception;

public class AlreadyRegisteredException extends RuntimeException {

    public AlreadyRegisteredException() {
        super("ALREADY_REGISTERED");
    }
}
