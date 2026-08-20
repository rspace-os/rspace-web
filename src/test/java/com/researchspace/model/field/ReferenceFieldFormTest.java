package com.researchspace.model.field;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.researchspace.model.core.GlobalIdPrefix;

class ReferenceFieldFormTest {

	@ParameterizedTest
	@EnumSource(GlobalIdPrefix.class)
	@DisplayName("Valid global Id prefixes are accepted")
	void testValidate(GlobalIdPrefix gIdPrefix) {
		String id = gIdPrefix.name() + RandomStringUtils.randomNumeric(1, 6);
		ReferenceFieldForm rff = new ReferenceFieldForm("referenceField");
		assertFalse(rff.validate(id).hasErrorMessages());
	}
	
	@Test
	@DisplayName("Valid syntax, not-real prefix is rejected")
	void rejectNonExistentPrefix() {
		String id = "ZZ1234";
		ReferenceFieldForm rff = new ReferenceFieldForm("referenceField");
		ErrorList errors = rff.validate(id);
		assertTrue(errors.hasErrorMessages());
	}
	
	@ParameterizedTest
	@DisplayName("Validate comma-separated multi-values")
	@ValueSource(strings = {"GL123,FL124","GL123, FL124","GL123 ,FL124", " GL123 , FL124, SD12345 "})
	void validateCommaSepList(String data) {
		ReferenceFieldForm rff = new ReferenceFieldForm("referenceField");
		assertFalse(rff.validate(data).hasErrorMessages());
	}
	
	@Test
	@DisplayName("All values must be valid")
	void invalidMultiValue() {
		String id = " GL123 , FL124,ZZ1234, SD12345 ";
		ReferenceFieldForm rff = new ReferenceFieldForm("referenceField");
		ErrorList errors = rff.validate(id);
		assertTrue(errors.hasErrorMessages());
	}

}
