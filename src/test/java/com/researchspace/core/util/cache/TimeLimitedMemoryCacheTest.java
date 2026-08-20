package com.researchspace.core.util.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.researchspace.core.util.cache.DefaultTimeLimitedMemoryCache;
import com.researchspace.core.util.cache.TimeLimitedMemoryCache;

public class TimeLimitedMemoryCacheTest {

	String license;
	TimeLimitedMemoryCache<String> cache;

	@Before
	public void setUp() throws Exception {
		license = "anything";
		cache = new DefaultTimeLimitedMemoryCache<>();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testCache() throws InterruptedException {
		assertNull(cache.getCachedItem());
		assertTrue(cache.isEmpty());
		// Olicense.setUniqueKey("a");
		cache.cache(license);
		assertTrue(cache.isValid());
		assertNotNull(cache.getCachedItem());
		assertEquals(license, cache.getCachedItem());

		cache.clear();
		assertNull(cache.getCachedItem());

		cache.cache(license);
		cache.setCacheTimeMillis(1L);
		// shortest cache time
		Thread.sleep(10);
		assertTrue(cache.isStale());

		cache.setCacheTimeMillis(10000L);
		assertTrue(cache.isValid());

	}

}
