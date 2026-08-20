package com.researchspace.maintenance.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.Before;
import org.junit.jupiter.api.Test;

public class WhiteListedSysAdminIPAddressTest {

	WhiteListedSysAdminIPAddress ip1, ip2;
	@Before
	public  void setUp() throws Exception {
	}

	@Test
	public void testEqualsHashCodeAndToString() {
		ip1 = new WhiteListedSysAdminIPAddress("abc");
		ip2 = new WhiteListedSysAdminIPAddress("abc");
		assertEquals(ip1, ip2);
		assertEquals(ip1.hashCode(), ip2.hashCode());
		assertTrue(ip1.toString().contains("abc"));
	}

}
