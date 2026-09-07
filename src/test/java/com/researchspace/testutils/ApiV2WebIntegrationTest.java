package com.researchspace.testutils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.jdbc.SqlScriptsTestExecutionListener;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.web.WebAppConfiguration;

/**
 * JUnit 5 web integration context for REST API v2 tests.
 *
 * <p>Composition rather than inheritance. The older {@code MVCTestBase} chain bottoms out in {@code
 * AbstractJUnit4SpringContextTests}, so anything extending it is a JUnit 4 test on the vintage
 * engine and cannot use {@code @Nested}, {@code @ParameterizedTest} or {@code @Tag}. A test
 * annotated with this gets the same Spring context through {@link SpringExtension} while staying
 * plain Jupiter, and inherits no fixture helpers it does not use.
 *
 * <p>The locations, profile and listeners deliberately match what a {@code MVCTestBase} subclass
 * merges to, so Spring's context cache key is identical and these tests share the already-built
 * context rather than paying for a second one. {@code dispatcher-test-servlet.xml} component-scans
 * {@code WebConfig}, so the production interceptor and exception-resolver chain is in play — that
 * is the whole point of testing at this layer.
 *
 * <p>Listeners match the base class, which notably excludes {@code
 * TransactionalTestExecutionListener}: work commits for real rather than rolling back. Keep tests
 * using this annotation read-only, or have them clean up after themselves.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Tag("integration")
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ActiveProfiles(profiles = "dev")
@ContextConfiguration(
    locations = {
      "classpath:applicationContext-resources.xml",
      "classpath:applicationContext-dao.xml",
      "classpath:applicationContext-test-service.xml",
      "classpath:dispatcher-test-servlet.xml"
    })
@TestExecutionListeners(
    value = {DependencyInjectionTestExecutionListener.class, SqlScriptsTestExecutionListener.class})
public @interface ApiV2WebIntegrationTest {}
