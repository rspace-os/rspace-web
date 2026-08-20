package com.researchspace.model.comms;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HTMLCleanerTest {

	HTMLCleaner rtu;
	@Before
	public void setUp() throws Exception {
		rtu = new HTMLCleaner();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testCleanHTMLStrict() {
		
			String plain = "Plain text with no html";
			assertEquals(plain, rtu.cleanHTMLStrict(plain,true));
			String plainWithNewline = "Plain text \n with newline";
			assertThat( rtu.cleanHTMLStrict(plainWithNewline,true), containsString("<br>"));
			
			String basicHTML = "Simple html <a href='http://www.google.com'>Google</a>";
			assertThat( rtu.cleanHTMLStrict(basicHTML,true), allOf(containsString("href=\"http://www.google.com\""),
					containsString("Google"), containsString("rel=\"nofollow\"")));
			
			basicHTML = "Simple html <a href='http://www.google.com' target='_blank'>Google</a>";
			assertThat( rtu.cleanHTMLStrict(basicHTML,true), allOf(containsString("href=\"http://www.google.com\""),
					containsString("Google"), containsString("rel=\"nofollow\""), containsString("target=\"_blank\"")));
			
			String scriptHTML = "Simple html <script>alert(1);</script>";
			String processedscriptHTML =  rtu.cleanHTMLStrict(scriptHTML,true);
			assertThat(processedscriptHTML, allOf(not(containsString("<script>")), not(containsString("alert(1)"))));
			
			String basicLink = "Simple link \nhttp://www.google.com ";
			String taggedhttp =  rtu.cleanHTMLStrict(basicLink,true);
			String EXPECTED = "<a href='http://www.google.com' rel='nofollow' class='word-wrap' target='_blank'>";
			assertThat(taggedhttp, containsString(EXPECTED));
			
			String basicLink2 = "http://www.google.com google";
			String taggedhttp2 =  rtu.cleanHTMLStrict(basicLink2,true);
			String EXPECTED2 = "<a href='http://www.google.com' rel='nofollow' class='word-wrap' target='_blank'>";
			assertThat(taggedhttp2, containsString(EXPECTED2));
			
			String twoLinks = "http://www.google.com google http://www.bbc.co.uk bbc";
			String taggedtwoLinks =  rtu.cleanHTMLStrict(twoLinks,true);
			String EXPECTED3 = "<a href='http://www.google.com' rel='nofollow' class='word-wrap' target='_blank'>";
			String EXPECTED4 = "<a href='http://www.bbc.co.uk' rel='nofollow' class='word-wrap' target='_blank'>";
			assertThat(taggedtwoLinks, allOf(containsString(EXPECTED3), containsString(EXPECTED4)));
			
			String httpsLink = "Simple link https://www.google.com ";
			String taggedhttps =  rtu.cleanHTMLStrict(httpsLink,true);
			EXPECTED = "<a href='https://www.google.com' rel='nofollow' class='word-wrap' target='_blank'>";
			assertThat(taggedhttps, containsString(EXPECTED));
			
	        taggedhttps = rtu.cleanHTMLStrict(httpsLink, false);
	        assertThat(taggedhttps, not(containsString(EXPECTED)));		 
		
	}

}
