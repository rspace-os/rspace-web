package com.researchspace.model.record;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Date;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.researchspace.model.User;

public class FormUsageTest {

	FormUsage formUsage;
	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testFormUsageUserFormInitialState() throws InterruptedException {
		long currTime = new Date().getTime();
		Thread.sleep(5);
		createFormUsage();
		assertNull(formUsage.getId());
		
		assertNotNull(formUsage.getLastUsedTimeInMillis());
		assertTrue(formUsage.getLastUsedTimeInMillis() > currTime);
		
	}

	private void createFormUsage() {
		User u = TestFactory.createAnyUser("user");
		RSForm t = TestFactory.createAnyForm();
		formUsage = new FormUsage(u, t);
	}

}
