package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.session.SessionAttributeUtils;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.Test;

public class SessionAttributeUtilsTest {

  @Test
  public void testGetTimeZoneFromSessionAttribute() {
    // returns UTC if value is null or empty
    DateTimeZone tz = SessionAttributeUtils.getTimeZoneFromSessionAttribute(null);
    assertEquals(tz, DateTimeZone.forID("UTC"));

    // valid string
    DateTimeZone tz2 = SessionAttributeUtils.getTimeZoneFromSessionAttribute("Asia/Irkutsk");
    assertEquals(tz2, DateTimeZone.forID("Asia/Irkutsk"));
  }

  @Test
  public void testUnrecognisedTZThrowsIAE() {
    assertThrows(
        IllegalArgumentException.class,
        () -> SessionAttributeUtils.getTimeZoneFromSessionAttribute("unknonwnn"));
  }
}
