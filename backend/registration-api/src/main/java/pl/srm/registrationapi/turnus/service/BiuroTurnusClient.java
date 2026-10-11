package pl.srm.registrationapi.turnus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;
import pl.srm.registrationapi.turnus.model.Turnus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Component
public class BiuroTurnusClient {
    private final RestClient client;
    private final ObjectMapper mapper;

    public BiuroTurnusClient(ObjectMapper mapper,
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
    }

    public List<Turnus> fetch() throws Exception {
        String body = client.get().uri("/internal/turnuses").retrieve().body(String.class);
        var root = mapper.readTree(body);
        if (root == null || !root.isArray()) throw new IllegalArgumentException("Expected Turnus catalogue array");
        List<Turnus> result = new ArrayList<>();
        var codes = new HashSet<String>();
        for (var node : root) {
            if (!node.path("active").isBoolean() || !node.path("registrationOpen").isBoolean())
                throw new IllegalArgumentException("Missing Turnus availability flags");
            Turnus turnus = mapper.treeToValue(node, Turnus.class);
            if (turnus.turnusCode() == null || turnus.turnusCode().isBlank()
                    || !codes.add(turnus.turnusCode()) || turnus.turnusName() == null || turnus.turnusName().isBlank()
                    || turnus.turnusLocation() == null || turnus.turnusLocation().isBlank()
                    || turnus.seasonType() == null || turnus.seasonYear() == null
                    || turnus.startDate() == null || turnus.endDate() == null
                    || turnus.endDate().isBefore(turnus.startDate())
                    || turnus.minAge() == null || turnus.minAge() < 0 || turnus.minAge() > 120
                    || turnus.capacity() == null || turnus.capacity() < 1)
                throw new IllegalArgumentException("Invalid Turnus catalogue definition");
            result.add(turnus);
        }
        return List.copyOf(result);
    }
}
