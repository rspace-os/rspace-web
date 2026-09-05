package com.researchspace.testutils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Registers {@link SpringExtension} without requiring a Spring test superclass.
 *
 * <p>Combine with {@link DefaultTestContext} or {@code @ContextConfiguration}. Do not declare
 * {@code @TestExecutionListeners} here because that replaces Spring's default listeners.
 */
@ExtendWith(SpringExtension.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface WithSpringContext {}
