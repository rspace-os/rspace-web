package com.researchspace.model.permissions;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GroupConstraintTest {

	GroupConstraint g1, g2,g3;
	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testSatisfies() {
		g1 = new GroupConstraint("g1");
		g2 = new GroupConstraint("g1");
		g3 = new GroupConstraint("g3");
		assertTrue(g1.satisfies(g2));
		assertTrue(g2.satisfies(g1));
		assertFalse(g2.satisfies(g3));
	}

}
