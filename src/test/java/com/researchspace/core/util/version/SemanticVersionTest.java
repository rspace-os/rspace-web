package com.researchspace.core.util.version;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SemanticVersionTest {
	SemanticVersion version;

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testSemanticVersion() {

		assertArgumentIsInvalid(null, null, null, null);
		assertArgumentIsInvalid(1, null, 1, "suffix");
		assertArgumentIsInvalid(1, null, null, "suffix");
		assertArgumentIsInvalid(1, 2, null, "suffix");
		// these are all valid
		new SemanticVersion(1, null, null, null);
		new SemanticVersion(1, 2, null, null);
		new SemanticVersion(1, 2, 3, null);
		new SemanticVersion(1, 2, 3, "any");

	}

	@Test
	public void testCompareSemanticVersion() {
		version = new SemanticVersion("2.3.4.suffix");
		assertTrue(version.isNewerThan(new SemanticVersion("2.2.17.later")));
		assertTrue(version.isNewerThan(new SemanticVersion("1.9.23.later")));
		assertTrue(version.isNewerThan(new SemanticVersion("1")));
		assertTrue(version.isNewerThan(new SemanticVersion("2.2")));
		assertTrue(version.isNewerThan(new SemanticVersion("2.3.4.suffiw")));

		assertTrue(version.isOlderThan(new SemanticVersion("2.3.17.later")));
		assertTrue(version.isOlderThan(new SemanticVersion("3.9.23.later")));
		assertTrue(version.isOlderThan(new SemanticVersion("3")));
		assertTrue(version.isOlderThan(new SemanticVersion("3.2")));
		assertTrue(version.isOlderThan(new SemanticVersion("3.3.4.suffiw")));

	}

	@Test
	public void testSemanticVersionFromString() {

		SemanticVersion EXPECTED = new SemanticVersion(1, null, null, null);
		SemanticVersion t1 = new SemanticVersion("1");
		assertEquals(EXPECTED, t1);
		assertEquals(EXPECTED, new SemanticVersion(t1.getString()));

		SemanticVersion EXPECTED2 = new SemanticVersion(1, 2, null, null);
		SemanticVersion t2 = new SemanticVersion("1.2");
		assertEquals(EXPECTED2, t2);
		assertEquals(EXPECTED2, new SemanticVersion(t2.getString()));

		SemanticVersion EXPECTED3 = new SemanticVersion(1, 2, 3, null);
		SemanticVersion t3 = new SemanticVersion("1.2.3");
		assertEquals(EXPECTED3, t3);
		assertEquals(EXPECTED3, new SemanticVersion(t3.getString()));

		SemanticVersion EXPECTED4 = new SemanticVersion(1, 2, 3, "RELEASE");
		SemanticVersion t4 = new SemanticVersion("1.2.3.RELEASE");

		assertEquals(EXPECTED4, t4);
		assertEquals(EXPECTED4, new SemanticVersion(t4.getString()));

		SemanticVersion EXPECTED5 = new SemanticVersion(1, 2, 3, "RELEASE");
		assertEquals(EXPECTED5, new SemanticVersion("1.2.3.RELEASE"));

	}

	void assertArgumentIsInvalid(Integer major, Integer minor, Integer qual, String suff) {
		boolean iseThrown = false;
		try {
			new SemanticVersion(null, null, null, null);
		} catch (IllegalArgumentException iae) {
			iseThrown = true;
		}
		assertTrue(iseThrown);
	}

	@Test
	public void testCompareTo() {

	}

}
