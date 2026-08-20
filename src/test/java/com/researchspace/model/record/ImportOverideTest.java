package com.researchspace.model.record;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.researchspace.model.User;

class ImportOverideTest {

	@Test
	void constructorInvariants() {
		assertThrows(IllegalArgumentException.class, ()->new ImportOverride(null, Instant.now(), null));
		assertThrows(IllegalArgumentException.class, ()->new ImportOverride(Instant.now(), null, null));
		
		final int TOLERANCE_EXCEEDED_SECONDS = ImportOverride.TOLERANCE_SECONDS + 1;
		assertThrows(IllegalArgumentException.class, ()->new ImportOverride(Instant.now(),
				Instant.now().minusSeconds(TOLERANCE_EXCEEDED_SECONDS), "someusername"));
	}
	
	@Test
	void constructorOK() {
		ImportOverride imp = new ImportOverride(Instant.now(), Instant.now().plusSeconds(500), "someusername");
		assertTrue(imp.getLastModified().isAfter(imp.getCreated()));
	}
	
	@Test
	void usernameTruncatedToFitDBConstraint() {
		String TOO_LONG_USERNAME = randomAlphabetic(User.MAX_UNAME_LENGTH + 1);
		ImportOverride imp = new ImportOverride(Instant.now(), Instant.now().plusSeconds(500), TOO_LONG_USERNAME);
		assertEquals(User.MAX_UNAME_LENGTH, imp.getOriginalCreatorUsername().length());
	}

}
