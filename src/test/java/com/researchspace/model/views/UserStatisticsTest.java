package com.researchspace.model.views;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class UserStatisticsTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	//RSPAC-1200
	@Test
	public void testGetUsedLicenseSeats() {
		UserStatistics stats = new UserStatistics(13, 13, 0, 5);
		stats.setTotalEnabledRSpaceAdmins(1);
		stats.setTotalEnabledSysAdmins(4);
		assertEquals(8, stats.getUsedLicenseSeats());
	}

}
