package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jayway.jsonpath.JsonPath;
import com.researchspace.core.util.JacksonUtil;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;

public class ApiAuditEventTest {

  @Test
  public void testSerialiseTimestampToISO8601() {
    ApiActivity event = new ApiActivity();
    event.setTimestampMillis(new Date().getTime());
    String json = JacksonUtil.toJson(event);
    String tstamp = JsonPath.parse(json).read("$.timestamp", String.class);
    assertNotNull(Instant.parse(tstamp));
  }
}
