package com.stockapp;

import com.stockapp.domain.screening.ScreeningRunRunner;
import com.stockapp.domain.screening.realtime.OperationalRealtimeScreeningScheduler;
import com.stockapp.domain.stock.DailyHistoryBootstrapRunner;
import com.stockapp.domain.stock.DailyPriceFinalizationScheduler;
import com.stockapp.domain.stock.DailyPriceFinalizationStartupRecoveryRunner;
import com.stockapp.domain.stock.DailyPriceInitialLoadRunner;
import com.stockapp.domain.stock.DailyPriceUpdateRunner;
import com.stockapp.domain.stock.DailyPriceUpdateScheduler;
import com.stockapp.domain.stock.StockPriceScheduler;
import com.stockapp.external.kis.KisWebSocketStartupRunner;
import com.stockapp.global.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyHistoryBootstrapProfileIsolationTest {

    @Test
    void dedicatedProfileEnablesOnlyBootstrapAutomaticEntryPoint()
            throws IOException {
        StandardEnvironment environment = environment(
                "local", "daily-history-bootstrap");

        assertThat(matchesProfile(DailyHistoryBootstrapRunner.class, environment))
                .isTrue();
        assertThat(matchesProfile(DailyPriceInitialLoadRunner.class, environment))
                .isFalse();
        assertThat(matchesProfile(DailyPriceUpdateRunner.class, environment))
                .isFalse();
        assertThat(matchesProfile(ScreeningRunRunner.class, environment))
                .isFalse();
        assertThat(matchesProfile(KisWebSocketStartupRunner.class, environment))
                .isFalse();
        assertThat(matchesProfile(StockPriceScheduler.class, environment))
                .isFalse();
        assertThat(matchesProfile(DailyPriceUpdateScheduler.class, environment))
                .isFalse();
        assertThat(matchesProfile(
                DailyPriceFinalizationStartupRecoveryRunner.class, environment))
                .isFalse();
        assertThat(matchesProfile(
                DailyPriceFinalizationScheduler.class, environment)).isFalse();
        assertThat(matchesProfile(
                OperationalRealtimeScreeningScheduler.class, environment))
                .isFalse();
        assertThat(matchesProfile(securityFilterChainMethod(), environment))
                .isFalse();

        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(
                "daily-history-bootstrap",
                new org.springframework.core.io.ClassPathResource(
                        "application-daily-history-bootstrap.yaml"));
        PropertySource<?> properties = sources.getFirst();
        assertThat(properties.getProperty("spring.main.web-application-type"))
                .isEqualTo("none");
        assertThat(properties.getProperty("spring.task.scheduling.enabled"))
                .isEqualTo(false);
        assertThat(properties.getProperty("spring.devtools.restart.enabled"))
                .isEqualTo(false);
        assertThat(properties.getProperty("spring.devtools.livereload.enabled"))
                .isEqualTo(false);
        assertThat(properties.getProperty("spring.devtools.add-properties"))
                .isEqualTo(false);
    }

    @Test
    void normalLocalProfileDoesNotEnableBootstrapRunner() {
        assertThat(matchesProfile(
                DailyHistoryBootstrapRunner.class, environment("local")))
                .isFalse();
    }

    @Test
    void applicationClosesContextAfterSuccessfulBootstrap() {
        ConfigurableApplicationContext context =
                mock(ConfigurableApplicationContext.class);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local", "daily-history-bootstrap");
        when(context.getEnvironment()).thenReturn(environment);

        StockSignalBackendApplication.closeAfterBatch(context);

        verify(context).close();
    }

    private StandardEnvironment environment(String... profiles) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    private boolean matchesProfile(
            Class<?> component,
            StandardEnvironment environment
    ) {
        Profile profile = component.getAnnotation(Profile.class);
        return environment.acceptsProfiles(Profiles.of(profile.value()));
    }

    private boolean matchesProfile(
            Method beanMethod,
            StandardEnvironment environment
    ) {
        Profile profile = beanMethod.getAnnotation(Profile.class);
        return environment.acceptsProfiles(Profiles.of(profile.value()));
    }

    private Method securityFilterChainMethod() {
        return java.util.Arrays.stream(SecurityConfig.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("filterChain"))
                .findFirst()
                .orElseThrow();
    }
}
