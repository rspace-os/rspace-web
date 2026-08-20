package com.researchspace.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Date;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TokenBasedVerificationTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testUserPasswordChangeUserDate() {
		// date is optional
		TokenBasedVerification upc = new TokenBasedVerification("a@b.com", null,TokenBasedVerificationType.PASSWORD_CHANGE);
		assertNotNull(upc.getRequestTime());
		assertNotNull(upc.getToken());
		assertFalse(upc.isResetCompleted());// initially false
	}
	
	@Test
	public void testUserPasswordChangeIsValid() {
		// date is optional
		TokenBasedVerification upc = new TokenBasedVerification("a@b.com", new Date(),TokenBasedVerificationType.PASSWORD_CHANGE);
		assertTrue(upc.isValidLink(upc.getToken(), TokenBasedVerificationType.PASSWORD_CHANGE));
		assertFalse(upc.isValidLink("wrongtoken", TokenBasedVerificationType.PASSWORD_CHANGE));
		
		// now set date in the past, is inactive
		TokenBasedVerification upc2 = new TokenBasedVerification("a@b.com", new Date(10),TokenBasedVerificationType.PASSWORD_CHANGE);
		assertFalse(upc2.isValidLink(upc.getToken(), TokenBasedVerificationType.PASSWORD_CHANGE));
		
	}

}
