package pl.srm.biuroapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"registration.api.password=test-service-password",
                "spring.datasource.url=jdbc:h2:mem:biuro-context;MODE=MariaDB;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa", "spring.datasource.password="})
class BiuroApiApplicationContextTest {

    @Test
    void contextLoads() {
    }
}
