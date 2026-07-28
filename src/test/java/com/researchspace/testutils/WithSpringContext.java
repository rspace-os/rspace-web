package com.researchspace.testutils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Wires a test class into the Spring TestContext framework.
 *
 * <p>This is the composed-annotation equivalent of extending a Spring base class. JUnit 4 needed a
 * base class because a runner is class-level and cannot be composed, which is why Spring shipped
 * {@code AbstractJUnit4SpringContextTests}; Jupiter registers the equivalent behaviour through an
 * extension instead, so the single superclass a test gets to spend stays free.
 *
 * <p>Deliberately adds nothing but the extension. In particular it declares no
 * {@code @TestExecutionListeners}: naming any set here would replace Spring's defaults for every
 * annotated class that does not declare its own, silently dropping listeners such as {@code
 * ServletTestExecutionListener} and {@code TransactionalTestExecutionListener}.
 *
 * <p>Combine it with a context annotation such as {@link DefaultTestContext}, or with
 * {@code @ContextConfiguration} directly.
 */
@ExtendWith(SpringExtension.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface WithSpringContext {}
