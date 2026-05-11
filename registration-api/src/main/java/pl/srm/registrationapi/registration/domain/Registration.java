
package pl.srm.registrationapi.registration.domain;

import java.time.LocalDateTime;

public record Registration(

        String registrationCode,
        String turnusCode,
        String personKey,
        String status,
        LocalDateTime createdAt,
        String payload


) {

}
