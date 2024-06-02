package com.researchspace.service;

import static org.junit.Assert.assertEquals;

import com.researchspace.session.SessionAttributeUtils;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SessionAttributeUtilsTest {

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testGetTimeZoneFromSessionAttribute() {
    // returns UTC if value is null or empty
    DateTimeZone tz = SessionAttributeUtils.getTimeZoneFromSessionAttribute(null);
    assertEquals(tz, DateTimeZone.forID("UTC"));

    // valid string
    DateTimeZone tz2 = SessionAttributeUtils.getTimeZoneFromSessionAttribute("Asia/Irkutsk");
    assertEquals(tz2, DateTimeZone.forID("Asia/Irkutsk"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUnrecognisedTZThrowsIAE() {
    SessionAttributeUtils.getTimeZoneFromSessionAttribute("unknonwnn");
  }
}
