package com.researchspace.model;

import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.researchspace.core.util.version.SemanticVersion;


public class AppVersionTest {
	 AppVersion version;
	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	
	@Test
	public void testCompareAppVersion() {
		version = createAppVersion("2.3.4.suffix");
		assertTrue(version.isNewerThan(createAppVersion("2.2.17.later")));
		assertTrue(version.isNewerThan(createAppVersion("1.9.23.later")));
		assertTrue(version.isNewerThan(createAppVersion("1")));
		assertTrue(version.isNewerThan(createAppVersion("2.2")));
		assertTrue(version.isNewerThan(createAppVersion("2.3.4.suffiw")));
		
		assertTrue(version.isOlderThan(createAppVersion("2.3.17.later")));
		assertTrue(version.isOlderThan(createAppVersion("3.9.23.later")));
		assertTrue(version.isOlderThan(createAppVersion("3")));
		assertTrue(version.isOlderThan(createAppVersion("3.2")));
		assertTrue(version.isOlderThan(createAppVersion("3.3.4.suffiw")));
	
	}

	private AppVersion createAppVersion(String version) {
		return new AppVersion(new SemanticVersion(version));
	}

}
