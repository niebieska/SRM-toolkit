package pl.srm.registrationapi.turnus.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import pl.srm.registrationapi.common.config.TurnusSchedulingConfig;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TurnusCacheSchedulingTest {
    @Test void startsWithoutCatalogueAndSchedulerRetriesThenRefreshes() throws Exception {
        var client = mock(BiuroTurnusClient.class);
        var calls = new AtomicInteger();
        var refreshed = new CountDownLatch(1);
        when(client.fetch()).thenAnswer(invocation -> {
            int call = calls.incrementAndGet();
            if (call == 1) throw new IllegalStateException("Biuro not ready");
            if (call >= 3) refreshed.countDown();
            return List.of();
        });
        new ApplicationContextRunner().withUserConfiguration(Config.class)
                .withBean(BiuroTurnusClient.class, () -> client)
                .withPropertyValues("turnus.catalogue.retry-interval=PT0.01S",
                        "turnus.catalogue.refresh-interval=PT0.05S")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(refreshed.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThat(context.getBean(TurnusProvider.class).getAvailableTurnuses()).isEmpty();
                    assertThat(calls.get()).isGreaterThanOrEqualTo(3);
                });
    }

    @Configuration
    @Import({CachedTurnusProvider.class, TurnusSchedulingConfig.class})
    static class Config {
        @Bean static PropertySourcesPlaceholderConfigurer placeholders() {
            return new PropertySourcesPlaceholderConfigurer();
        }
        @Bean static org.springframework.boot.convert.ApplicationConversionService conversionService() {
            return new org.springframework.boot.convert.ApplicationConversionService();
        }
    }
}
