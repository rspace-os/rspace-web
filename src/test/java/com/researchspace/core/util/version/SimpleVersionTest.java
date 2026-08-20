package com.researchspace.core.util.version;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


public class SimpleVersionTest {
	SimpleVersion v1, v2, v3;

	@BeforeEach
	public void setUp() throws Exception {
		v1 = null;
		v2 = null;
		v3 = null;
		setUpSimpleVersions();
	}

	@Test
	public void testNullConstructorThrowsIAE() {
		assertThrows(IllegalArgumentException.class, ()->new SimpleVersion(null));
	}

	@Test
	public void testEqualsObject() {
		SimpleVersion v1Equal = new SimpleVersion(1L);
		assertEquals(v1, v1Equal);
	}

	@Test
	public void testHashCode() {
		SimpleVersion v1Equal = new SimpleVersion(1L);
		assertTrue(v1.hashCode() == v1Equal.hashCode());
	}

	@Test
	public void testSimpleVersion() {

		assertEquals(Long.valueOf(1), v1.getVersion());
	}

	@Test
	public void testIncrementSimpleVersionReturnsNewSimpleVersion() {

		SimpleVersion v2 = v1.increment();
		assertEquals(Long.valueOf(2), v2.getVersion());
		// v1 is unchanged
		assertEquals(Long.valueOf(1), v1.getVersion());

	}

	@Test
	public void testCompareTo() {

		SimpleVersion v4 = new SimpleVersion(3L);
		assertEquals(-1, v1.compareTo(v2));
		assertEquals(1, v3.compareTo(v2));
		assertEquals(0, v3.compareTo(v4));
	}

	private void setUpSimpleVersions() {
		v1 = new SimpleVersion(1L);
		v2 = new SimpleVersion(2L);
		v3 = new SimpleVersion(3L);
	}

	@Test
	public void testBefore() {
		assertTrue(v1.before(v2));
		assertFalse(v2.before(v1));
	}

	@Test
	public void testAfter() {
		assertTrue(v2.after(v1));
		assertFalse(v1.after(v3));
	}

	@Test
	public void testToString() {
		assertTrue(v1.toString().length() > 0);
	}

	@Test
	public void testAsString() {
		assertEquals("1", v1.asString());
	}

}
