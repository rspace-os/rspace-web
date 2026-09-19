package com.researchspace.core.util;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Date;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ResponseUtilsTest {
  @Mock HttpServletResponse resp;
  ResponseUtil util;

  @BeforeEach
  public void setUp() throws Exception {
    util = new ResponseUtil();
  }

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testSetCacheTimeInBrowserIntDateHttpHeaders() {
    Date date = new Date();
    util.setCacheTimeInBrowser(1000, date, resp);
    Mockito.verify(resp).addHeader(ResponseUtil.CACHE_CONTROL_HDR, "max-age=1000");

    util.setCacheTimeInBrowser(1000, null, resp); // null is ok
  }
}
