package com.researchspace.core.util;

import static com.researchspace.core.util.DateUtil.convertDateToISOFormat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.Test;

public class DateUtilTest {

  private class DateUtilTSS extends DateUtil {
    DateTimeZone tz;

    // override session mechanism
    DateTimeZone getUserTimezone() {
      return tz;
    }
  }

  @Test
  public void testconvertToUTCTimeZone() throws ParseException {

    TimeZone localTz = TimeZone.getTimeZone("Asia/Irkutsk");
    // mimic HTTP Session
    DateUtilTSS dateUtiltss = new DateUtilTSS();
    // mimic HTTP Session
    dateUtiltss.tz = DateTimeZone.forTimeZone(localTz);

    TimeZone utctz = TimeZone.getTimeZone("UTC");
    Calendar cal = Calendar.getInstance();
    cal.setTimeZone(localTz);

    SimpleDateFormat df = new SimpleDateFormat();
    df.setTimeZone(TimeZone.getTimeZone("Asia/Irkutsk"));
    String localTimeStr = (df.format(cal.getTime())); // + 9

    Date serverInputTimeForLocal = df.parse(localTimeStr);
  }

  final String TIME_ZERO_UTC = "1970-01-01T00:00:00.000Z";
  final String TIME_ZERO_PLUS2 = "1970-01-01T02:00:00.000+02:00";

  @Test
  public void testConvertDateTimeToISOFormat() {
    Date date = new Date(0);
    String isoDateUTC = DateUtil.convertDateToISOFormat(date, TimeZone.getTimeZone("UTC"));
    assertEquals(TIME_ZERO_UTC, isoDateUTC);

    isoDateUTC = DateUtil.convertDateToISOFormat(date, null);
    assertEquals(TIME_ZERO_UTC, isoDateUTC);

    String isoDateTurkey = DateUtil.convertDateToISOFormat(date, TimeZone.getTimeZone("Turkey"));
    assertEquals(TIME_ZERO_PLUS2, isoDateTurkey);
  }

  @Test
  public void testConvertDateIsoFormatToISOFormat() {

    Long isoDateUTC = DateUtil.convertISODateToMillis("2001-12-25");
    Calendar cal = Calendar.getInstance();
    cal.setTime(new Date(isoDateUTC));
    assertEquals(2001, cal.get(Calendar.YEAR));
  }

  @Test
  public void testConvertMillisToISOFormat() {
    String isoDateUTC = DateUtil.convertDateToISOFormat(0L, TimeZone.getTimeZone("UTC"));
    assertEquals(TIME_ZERO_UTC, isoDateUTC);

    isoDateUTC = DateUtil.convertDateToISOFormat(0L, null);
    assertEquals(TIME_ZERO_UTC, isoDateUTC);

    String isoDateTurkey = DateUtil.convertDateToISOFormat(0L, TimeZone.getTimeZone("Turkey"));
    assertEquals(TIME_ZERO_PLUS2, isoDateTurkey);

    // and roundtrip
    assertEquals(0L, DateUtil.convertISO8601ToMillis(TIME_ZERO_PLUS2).longValue());
    assertEquals(0L, DateUtil.convertISO8601ToMillis(TIME_ZERO_UTC).longValue());
  }

  @Test
  public void testConvertMillisToISOFormatRequiresNotNull() {
    Long millis = null;
    TimeZone utc = TimeZone.getTimeZone("UTC");

    assertThrows(NullPointerException.class, () -> convertDateToISOFormat(millis, utc));
  }

  @Test
  public void testConvertDateToISOFormatRequiresNotNull() {
    Date date = null;
    TimeZone utc = TimeZone.getTimeZone("UTC");

    assertThrows(NullPointerException.class, () -> convertDateToISOFormat(date, utc));
  }
}
