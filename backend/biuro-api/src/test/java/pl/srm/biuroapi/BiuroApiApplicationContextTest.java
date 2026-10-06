package pl.srm.biuroapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "registration.api.password=test-service-password")
class BiuroApiApplicationContextTest {

    @Test
    void contextLoads() {
    }
}
