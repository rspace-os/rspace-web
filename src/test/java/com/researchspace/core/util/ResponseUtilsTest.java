package com.researchspace.core.util;

import java.util.Date;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ResponseUtilsTest {

	@Rule
	public MockitoRule rule = MockitoJUnit.rule();
	@Mock
	HttpServletResponse resp;
	ResponseUtil util;

	@Before
	public void setUp() throws Exception {
		util = new ResponseUtil();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testSetCacheTimeInBrowserIntDateHttpHeaders() {
		Date date = new Date();
		util.setCacheTimeInBrowser(1000, date, resp);
		Mockito.verify(resp).addHeader(ResponseUtil.CACHE_CONTROL_HDR, "max-age=1000");

		util.setCacheTimeInBrowser(1000, null, resp);// null is ok
	}

}
