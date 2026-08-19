package com.stockapp;

import com.stockapp.domain.screening.ScreeningRunRunner;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyPriceLoadProfileIsolationTest {

    @Test
    void dailyPriceLoadEnablesOnlyDailyPriceRunnerAmongAutomaticEntryPoints() {
        StandardEnvironment environment = environment("local", "daily-price-load");

        assertThat(matchesProfile(DailyPriceInitialLoadRunner.class, environment)).isTrue();
        assertThat(matchesProfile(KisWebSocketStartupRunner.class, environment)).isFalse();
        assertThat(matchesProfile(StockPriceScheduler.class, environment)).isFalse();
        assertThat(matchesProfile(DailyPriceUpdateRunner.class, environment)).isFalse();
        assertThat(matchesProfile(DailyPriceUpdateScheduler.class, environment)).isFalse();
        assertThat(matchesProfile(securityFilterChainMethod(), environment)).isFalse();
    }

    @Test
    void localProfileKeepsWebSocketRunnerAndSchedulerEnabled() {
        StandardEnvironment environment = environment("local");

        assertThat(matchesProfile(DailyPriceInitialLoadRunner.class, environment)).isFalse();
        assertThat(matchesProfile(KisWebSocketStartupRunner.class, environment)).isTrue();
        assertThat(matchesProfile(StockPriceScheduler.class, environment)).isTrue();
        assertThat(matchesProfile(DailyPriceUpdateRunner.class, environment)).isFalse();
        assertThat(matchesProfile(DailyPriceUpdateScheduler.class, environment)).isTrue();
        assertThat(matchesProfile(securityFilterChainMethod(), environment)).isTrue();
    }

    @Test
    void testProfileDisablesWebSocketStartupRunner() {
        StandardEnvironment environment = environment("local", "test");

        assertThat(matchesProfile(KisWebSocketStartupRunner.class, environment))
                .isFalse();
    }

    @Test
    void prodProfileKeepsSchedulerEnabled() {
        StandardEnvironment environment = environment("prod");

        assertThat(matchesProfile(DailyPriceInitialLoadRunner.class, environment)).isFalse();
        assertThat(matchesProfile(KisWebSocketStartupRunner.class, environment)).isFalse();
        assertThat(matchesProfile(StockPriceScheduler.class, environment)).isTrue();
        assertThat(matchesProfile(DailyPriceUpdateScheduler.class, environment)).isTrue();
        assertThat(matchesProfile(securityFilterChainMethod(), environment)).isTrue();
    }

    @Test
    void dailyPriceLoadProfileDisablesWebServerAndDevToolsRestart() throws IOException {
        List<PropertySource<?>> propertySources = new YamlPropertySourceLoader().load(
                "daily-price-load",
                new org.springframework.core.io.ClassPathResource(
                        "application-daily-price-load.yaml"));

        assertThat(propertySources).hasSize(1);
        PropertySource<?> properties = propertySources.getFirst();
        assertThat(properties.getProperty("spring.main.web-application-type"))
                .isEqualTo("none");
        assertThat(properties.getProperty("spring.devtools.restart.enabled"))
                .isEqualTo(false);
        assertThat(properties.getProperty("spring.devtools.livereload.enabled"))
                .isEqualTo(false);
        assertThat(properties.getProperty("spring.devtools.add-properties"))
                .isEqualTo(false);
    }

    @Test
    void dailyPriceUpdateProfileIsIsolatedAndNonWeb() throws IOException {
        StandardEnvironment environment = environment("local", "daily-price-update");

        assertThat(matchesProfile(DailyPriceInitialLoadRunner.class, environment)).isFalse();
        assertThat(matchesProfile(DailyPriceUpdateRunner.class, environment)).isTrue();
        assertThat(matchesProfile(DailyPriceUpdateScheduler.class, environment)).isFalse();
        assertThat(matchesProfile(KisWebSocketStartupRunner.class, environment)).isFalse();
        assertThat(matchesProfile(StockPriceScheduler.class, environment)).isFalse();
        assertThat(matchesProfile(securityFilterChainMethod(), environment)).isFalse();

        List<PropertySource<?>> propertySources = new YamlPropertySourceLoader().load(
                "daily-price-update",
                new org.springframework.core.io.ClassPathResource(
                        "application-daily-price-update.yaml"));
        PropertySource<?> properties = propertySources.getFirst();
        assertThat(properties.getProperty("spring.main.web-application-type"))
                .isEqualTo("none");
        assertThat(properties.getProperty("spring.devtools.restart.enabled"))
                .isEqualTo(false);
        assertThat(properties.getProperty("spring.devtools.livereload.enabled"))
                .isEqualTo(false);
        assertThat(properties.getProperty("spring.devtools.add-properties"))
                .isEqualTo(false);
    }

    @Test
    void screeningRunProfileEnablesOnlyScreeningRunnerAndIsNonWeb() throws IOException {
        StandardEnvironment environment = environment("local", "screening-run");

        assertThat(matchesProfile(ScreeningRunRunner.class, environment)).isTrue();
        assertThat(matchesProfile(DailyPriceInitialLoadRunner.class, environment)).isFalse();
        assertThat(matchesProfile(DailyPriceUpdateRunner.class, environment)).isFalse();
        assertThat(matchesProfile(DailyPriceUpdateScheduler.class, environment)).isFalse();
        assertThat(matchesProfile(KisWebSocketStartupRunner.class, environment)).isFalse();
        assertThat(matchesProfile(StockPriceScheduler.class, environment)).isFalse();
        assertThat(matchesProfile(securityFilterChainMethod(), environment)).isFalse();

        List<PropertySource<?>> propertySources = new YamlPropertySourceLoader().load(
                "screening-run",
                new org.springframework.core.io.ClassPathResource(
                        "application-screening-run.yaml"));
        PropertySource<?> properties = propertySources.getFirst();
        assertThat(properties.getProperty("spring.main.web-application-type"))
                .isEqualTo("none");
        assertThat(properties.getProperty("spring.devtools.restart.enabled"))
                .isEqualTo(false);
        assertThat(properties.getProperty("spring.devtools.livereload.enabled"))
                .isEqualTo(false);
        assertThat(properties.getProperty("spring.devtools.add-properties"))
                .isEqualTo(false);
    }

    @Test
    void schemaValidateProfileDisablesAutomaticEntryPointsAndIsNonWeb() throws IOException {
        StandardEnvironment environment = environment("local", "schema-validate");

        assertThat(matchesProfile(DailyPriceInitialLoadRunner.class, environment)).isFalse();
        assertThat(matchesProfile(DailyPriceUpdateRunner.class, environment)).isFalse();
        assertThat(matchesProfile(ScreeningRunRunner.class, environment)).isFalse();
        assertThat(matchesProfile(DailyPriceUpdateScheduler.class, environment)).isFalse();
        assertThat(matchesProfile(KisWebSocketStartupRunner.class, environment)).isFalse();
        assertThat(matchesProfile(StockPriceScheduler.class, environment)).isFalse();
        assertThat(matchesProfile(securityFilterChainMethod(), environment)).isFalse();

        List<PropertySource<?>> propertySources = new YamlPropertySourceLoader().load(
                "schema-validate",
                new org.springframework.core.io.ClassPathResource(
                        "application-schema-validate.yaml"));
        PropertySource<?> properties = propertySources.getFirst();
        assertThat(properties.getProperty("spring.main.web-application-type"))
                .isEqualTo("none");
        assertThat(properties.getProperty("spring.jpa.hibernate.ddl-auto"))
                .isEqualTo("validate");
        assertThat(properties.getProperty("spring.devtools.restart.enabled"))
                .isEqualTo(false);
        assertThat(properties.getProperty("spring.devtools.livereload.enabled"))
                .isEqualTo(false);
        assertThat(properties.getProperty("spring.devtools.add-properties"))
                .isEqualTo(false);
    }

    @Test
    void applicationClosesContextAfterDailyPriceLoadStartupCompletes() {
        ConfigurableApplicationContext context = org.mockito.Mockito.mock(
                ConfigurableApplicationContext.class);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("daily-price-load");
        when(context.getEnvironment()).thenReturn(environment);

        StockSignalBackendApplication.closeAfterBatch(context);

        verify(context).close();
    }

    @Test
    void applicationClosesContextAfterDailyPriceUpdateStartupCompletes() {
        ConfigurableApplicationContext context = org.mockito.Mockito.mock(
                ConfigurableApplicationContext.class);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("daily-price-update");
        when(context.getEnvironment()).thenReturn(environment);

        StockSignalBackendApplication.closeAfterBatch(context);

        verify(context).close();
    }

    @Test
    void applicationClosesContextAfterScreeningRunStartupCompletes() {
        ConfigurableApplicationContext context = org.mockito.Mockito.mock(
                ConfigurableApplicationContext.class);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("screening-run");
        when(context.getEnvironment()).thenReturn(environment);

        StockSignalBackendApplication.closeAfterBatch(context);

        verify(context).close();
    }

    @Test
    void applicationClosesContextAfterSchemaValidationCompletes() {
        ConfigurableApplicationContext context = org.mockito.Mockito.mock(
                ConfigurableApplicationContext.class);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local", "schema-validate");
        when(context.getEnvironment()).thenReturn(environment);

        StockSignalBackendApplication.closeAfterBatch(context);

        verify(context).close();
    }

    @Test
    void applicationKeepsContextOpenForNormalLocalProfile() {
        ConfigurableApplicationContext context = org.mockito.Mockito.mock(
                ConfigurableApplicationContext.class);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        when(context.getEnvironment()).thenReturn(environment);

        StockSignalBackendApplication.closeAfterBatch(context);

        verify(context, never()).close();
    }

    private StandardEnvironment environment(String... activeProfiles) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(activeProfiles);
        return environment;
    }

    private boolean matchesProfile(Class<?> component, StandardEnvironment environment) {
        Profile profile = component.getAnnotation(Profile.class);
        return environment.acceptsProfiles(Profiles.of(profile.value()));
    }

    private boolean matchesProfile(Method beanMethod, StandardEnvironment environment) {
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
