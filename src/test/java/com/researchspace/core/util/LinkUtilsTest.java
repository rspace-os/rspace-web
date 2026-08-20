package com.researchspace.core.util;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LinkUtilsTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void test() {
		String OK_HTTP =  "http://www.bbc.co.uk";
		String OK_HTTPS =  "https://www.bbc.co.uk";
		assertEquals(OK_HTTP, LinkUtils.modifyPrefix(OK_HTTP));
		assertEquals(OK_HTTPS, LinkUtils.modifyPrefix(OK_HTTPS));
		assertEquals(OK_HTTP, LinkUtils.modifyPrefix("www.bbc.co.uk"));
		assertEquals(OK_HTTP, LinkUtils.modifyPrefix("http:/www.bbc.co.uk"));
		assertEquals(OK_HTTP, LinkUtils.modifyPrefix("http:www.bbc.co.uk"));
		assertEquals(OK_HTTP, LinkUtils.modifyPrefix("http/www.bbc.co.uk"));
		assertEquals(OK_HTTPS, LinkUtils.modifyPrefix("https/www.bbc.co.uk"));
		assertEquals(OK_HTTPS, LinkUtils.modifyPrefix("https:www.bbc.co.uk"));
	}

}
