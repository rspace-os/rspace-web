package com.researchspace.testutils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Wires a test class into the Spring TestContext framework, leaving its single superclass free.
 * Combine with a context annotation such as {@link DefaultTestContext}, or
 * {@code @ContextConfiguration} directly.
 *
 * <p>Do not add {@code @TestExecutionListeners} here: naming any set replaces Spring's defaults for
 * every annotated class that does not declare its own, silently dropping listeners such as {@code
 * TransactionalTestExecutionListener}.
 */
@ExtendWith(SpringExtension.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface WithSpringContext {}
