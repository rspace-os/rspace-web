package com.researchspace.model.comms;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.immutable.ImmutableCalScale;
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion;
import net.fortuna.ical4j.transform.recurrence.Frequency;
import net.fortuna.ical4j.util.RandomUidGenerator;
import net.fortuna.ical4j.util.UidGenerator;

/** Generates a basic .ics file for a Request */
public class ICSEventGenerator {

  public net.fortuna.ical4j.model.Calendar createICalEventFor(MessageOrRequest mor) {
    Calendar icalendar = getIcal4jCalendarInstance();
    LocalDate date =
        mor.getRequestedCompletionDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

    // initialise as an all-day event..
    VEvent deadline = new VEvent(date, "RS request from " + mor.getOriginator().getFullName());

    Description des = new Description(mor.getMessage());
    deadline.setPropertyList(deadline.getPropertyList().add(des));
    // Generate a UID for the event..
    UidGenerator ug;

    ug = new RandomUidGenerator();
    deadline.setPropertyList(deadline.getPropertyList().add(ug.generateUid()));

    icalendar.setComponentList(icalendar.getComponentList().add(deadline));
    return icalendar;
  }

  public net.fortuna.ical4j.model.Calendar createICalEventFor(CalendarEvent event) {
    Calendar icalendar = getIcal4jCalendarInstance();
    Instant startDatetime =
        event.getStartTime() != null ? event.getStartTime().toInstant() : Instant.now();

    VEvent deadline;
    if (event.getEndTime() != null) {
      deadline = new VEvent(startDatetime, event.getEndTime().toInstant(), event.getTitle());
    } else {
      deadline = new VEvent(startDatetime, event.getTitle());
    }

    if (event.getFrequency() != null && event.getOccurrences() != null) {
      Recur<Instant> recur =
          new Recur<>(Frequency.valueOf(event.getFrequency()), event.getOccurrences().intValue());
      RRule<Instant> rrule = new RRule<>(recur);
      deadline.setPropertyList(deadline.getPropertyList().add(rrule));
    }

    if (event.getDescription() != null) {
      Description description = new Description(event.getDescription());
      deadline.setPropertyList(deadline.getPropertyList().add(description));
    }

    // Generate a UID for the event..
    UidGenerator ug;

    ug = new RandomUidGenerator();
    deadline.setPropertyList(deadline.getPropertyList().add(ug.generateUid()));

    icalendar.setComponentList(icalendar.getComponentList().add(deadline));
    return icalendar;
  }

  private Calendar getIcal4jCalendarInstance() {
    Calendar icalendar = new Calendar();
    icalendar.setPropertyList(
        icalendar
            .getPropertyList()
            .add(new ProdId("-//ResearchSpace//iCal4j 1.0//EN"))
            .add(ImmutableVersion.VERSION_2_0)
            .add(ImmutableCalScale.GREGORIAN));
    return icalendar;
  }
}
