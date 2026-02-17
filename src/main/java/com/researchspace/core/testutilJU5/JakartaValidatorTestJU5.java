package com.researchspace.core.testutilJU5;


import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.BeforeAll;

import lombok.extern.slf4j.Slf4j;
/**
 * Utility methods for testing Jakarta validation annotations on a class.
 * Provides a {@link Validator} instance to subclasses.
 *
 */
@Slf4j
public abstract class JakartaValidatorTestJU5 {
	
	protected static Validator validator;
	
	@BeforeAll
	public static void beforeClass() {
		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();	
	}
	
	/**
	 * Validates an object and asserts it has the expectedErrors, and prints errors.
	 */
	protected void assertNErrors (Object toValidate, int  expectedErrors) {
		assertNErrors(toValidate, expectedErrors, true);
	}
	/**
	 * 
	 * @param toValidate
	 * @param expectedErrors
	 * @param printMessages optional whether to print error messages
	 */
	protected void assertNErrors(Object toValidate, int expectedErrors, boolean printMessages) {
		Set<ConstraintViolation<Object>> violations = validator.validate(toValidate);
		if (printMessages) {
			violations.stream().map(ConstraintViolation::getMessage).forEach(log::error);
		}

		int violationCount = violations.size();
		assertEquals(expectedErrors, violationCount);

	}
	/**
	 * Asserts zero errors, i.e the object is valid.
	 * @param toValidate
	 */
	protected void assertValid (Object toValidate) {
		assertNErrors(toValidate, 0, true);
	}
}
