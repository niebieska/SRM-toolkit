package pl.srm.registrationapi.turnus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;
import pl.srm.registrationapi.turnus.model.Turnus;
import pl.srm.registrationapi.turnus.validator.TurnusCatalogueValidator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Component
public class BiuroTurnusClient {
    private final RestClient client;
    private final ObjectMapper mapper;
    private final TurnusCatalogueValidator validator;
    private final pl.srm.registrationapi.season.StaffCatalogueValidator staffValidator;

    public BiuroTurnusClient(ObjectMapper mapper, TurnusCatalogueValidator validator,
            pl.srm.registrationapi.season.StaffCatalogueValidator staffValidator,
            @Value("${turnus.catalogue.url}") String url,
            @Value("${turnus.catalogue.username}") String username,
            @Value("${turnus.catalogue.password}") String password,
            @Value("${turnus.catalogue.connect-timeout}") Duration connectTimeout,
            @Value("${turnus.catalogue.read-timeout}") Duration readTimeout) {
        Assert.hasText(username, "Turnus lookup username must not be blank");
        Assert.hasText(password, "Turnus lookup password must not be blank");
        Assert.isTrue(!connectTimeout.isNegative() && !connectTimeout.isZero(), "Connect timeout must be positive");
        Assert.isTrue(!readTimeout.isNegative() && !readTimeout.isZero(), "Read timeout must be positive");
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        client = RestClient.builder().baseUrl(url).requestFactory(factory)
                .defaultHeaders(headers -> headers.setBasicAuth(username, password)).build();
        this.mapper = mapper;
        this.validator = validator;
        this.staffValidator = staffValidator;
    }

    public List<Turnus> fetch() throws Exception {
        String body = client.get().uri("/internal/turnuses").retrieve().body(String.class);
        var root = mapper.readTree(body);
        if (root == null || !root.isArray()) throw new IllegalArgumentException("Expected Turnus catalogue array");
        List<Turnus> result = new ArrayList<>();
        var codes = new HashSet<String>();
        for (var node : root) {
            if (!node.path("active").isBoolean() || !node.path("participantRegistrationOpen").isBoolean()
                    || !node.path("staffRegistrationOpen").isBoolean())
                throw new IllegalArgumentException("Missing Turnus availability flags");
            Turnus turnus = mapper.treeToValue(node, Turnus.class);
            validator.validate(turnus);
            staffValidator.validate(turnus.staffCatalogue(), turnus.seasonType().name());
            if (!codes.add(turnus.turnusCode()))
                throw new IllegalArgumentException("Duplicate Turnus code");
            result.add(turnus);
        }
        return List.copyOf(result);
    }
}
