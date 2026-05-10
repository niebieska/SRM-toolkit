
package pl.srm.registrationapi.common.exception;

public class TurnusNotFoundException extends RuntimeException {
    public TurnusNotFoundException(String code) {
        super("Turnus not found: " + code);
    }
}
