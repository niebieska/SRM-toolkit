
package pl.srm.registrationapi.registration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RegistrationValidationRequest(

        @JsonProperty("turnusCode")
        String turnusCode,

        @JsonProperty("uczestnik")
        Person person

) {}
