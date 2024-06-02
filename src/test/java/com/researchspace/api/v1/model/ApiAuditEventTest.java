package com.researchspace.api.v1.model;

import static org.junit.Assert.assertNotNull;

import com.jayway.jsonpath.JsonPath;
import com.researchspace.core.util.JacksonUtil;
import java.time.Instant;
import java.util.Date;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ApiAuditEventTest {

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testSerialiseTimestampToISO8601() {
    ApiActivity event = new ApiActivity();
    event.setTimestampMillis(new Date().getTime());
    String json = JacksonUtil.toJson(event);
    String tstamp = JsonPath.parse(json).read("$.timestamp", String.class);
    assertNotNull(Instant.parse(tstamp));
  }
}
