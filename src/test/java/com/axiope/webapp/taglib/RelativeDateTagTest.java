package com.axiope.webapp.taglib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;

public class RelativeDateTagTest {

  @Test
  public void testGetStringNullDate() {
    RelativeDateTag tag = new RelativeDateTag();
    assertEquals("", tag.getString());
    tag.setInput(null);
    assertEquals("", tag.getString());
  }

  @Test
  public void testGetString() {
    final int HOUR = 22;
    DateTime localDateTime = new DateTime(2017, 1, 23, 19, HOUR);

    RelativeDateTag tag = new RelativeDateTag();
    tag.setNowTime(localDateTime);
    tag.setInput(localDateTime.toDate());
    assertThat(tag.getString()).contains("Today");

    DateTime yesterday = localDateTime.minusDays(1).minusMinutes(1);
    tag.setInput(yesterday.toDate());
    assertThat(tag.getString())
        .as("should contain 'Yesterday' but was: " + tag.getString())
        .contains("Yesterday");

    // check spans year gaps
    DateTime lastDayOfYear = localDateTime.year().setCopy(2013); // not a leap year
    lastDayOfYear = lastDayOfYear.dayOfYear().setCopy(365);

    DateTime firstDayOfNewYear = localDateTime.year().setCopy(2014); // not a leap year
    firstDayOfNewYear = firstDayOfNewYear.dayOfYear().setCopy(1);
    tag.setInput(lastDayOfYear.toDate());
    tag.setNowTime(firstDayOfNewYear);
    assertThat(tag.getString())
        .as("should contain 'Yesterday' but was: " + tag.getString())
        .contains("Yesterday");

    tag.setNowTime(null);
    DateTime secondDayOfNewYear = firstDayOfNewYear.dayOfYear().setCopy(2);
    tag.setNowTime(secondDayOfNewYear);
    tag.setRelativeForNDays(3);
    assertThat(tag.getString()).contains("2 days ago");
    tag.setNowTime(null);

    Date fourDaysAgo = localDateTime.minusDays(4).minusMinutes(1).toDate();
    tag.setInput(fourDaysAgo);
    tag.setNowTime(localDateTime);
    tag.setRelativeForNDays(5);
    assertThat(tag.getString()).contains("4 days ago ");

    // over a year ago
    DateTime fourDaysAndYearAgo = localDateTime.year().addToCopy(-1);
    fourDaysAndYearAgo = fourDaysAndYearAgo.dayOfMonth().addToCopy(-4);
    tag.setInput(fourDaysAndYearAgo.toDate());
    assertThat(tag.getString())
        .as("should contain day of the month but was: " + tag.getString())
        .contains("" + fourDaysAndYearAgo.dayOfMonth().get());

    DateTime lastDayTwoYearsAgo = lastDayOfYear.year().addToCopy(-1);
    tag.setInput(lastDayTwoYearsAgo.toDate());
    tag.setNowTime(firstDayOfNewYear);
    assertThat(tag.getString())
        .as("should contain '31' but was: " + tag.getString())
        .contains("31");
    assertThat(tag.getString())
        .as("should contain hour but was: " + tag.getString())
        .contains(HOUR + "");
  }
}
