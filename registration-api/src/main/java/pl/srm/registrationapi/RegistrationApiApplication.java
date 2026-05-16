package pl.srm.registrationapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RegistrationApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(RegistrationApiApplication.class, args);
    }

}
