package com.researchspace.model.comms;

import java.util.Date;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.Version;
import net.fortuna.ical4j.util.RandomUidGenerator;
import net.fortuna.ical4j.util.UidGenerator;

/**
 * Generates a basic .ics file for a Request
 */
public class ICSEventGenerator {

	public net.fortuna.ical4j.model.Calendar createICalEventFor(MessageOrRequest mor) {
		Calendar icalendar = getIcal4jCalendarInstance();
		java.util.Calendar calendar = java.util.Calendar.getInstance();

		Date date = mor.getRequestedCompletionDate();
		calendar.setTime(date);

		// initialise as an all-day event..
		VEvent deadline = new VEvent(new net.fortuna.ical4j.model.DateTime(calendar.getTime()),
				"RS request from " + mor.getOriginator().getFullName());

		Description des = new Description(mor.getMessage());
		deadline.getProperties().add(des);
		// Generate a UID for the event..
		UidGenerator ug;

		ug = new RandomUidGenerator();
		deadline.getProperties().add(ug.generateUid());

		icalendar.getComponents().add(deadline);
		return icalendar;
	}

	public net.fortuna.ical4j.model.Calendar createICalEventFor(CalendarEvent event) {
		Calendar icalendar = getIcal4jCalendarInstance();
		java.util.Calendar calendar = java.util.Calendar.getInstance();

		DateTime startDatetime = new DateTime((event.getStartTime() != null) ? event.getStartTime() : calendar.getTime());

		VEvent deadline;
		if (event.getEndTime() != null) {
			deadline = new VEvent(startDatetime, new net.fortuna.ical4j.model.DateTime(event.getEndTime().getTime()),
					event.getTitle());
		} else {
			deadline = new VEvent(startDatetime, event.getTitle());
		}

		if (event.getFrequency() != null && event.getOccurrences() != null) {
			Recur recur = new Recur(event.getFrequency(), event.getOccurrences().intValue());
			RRule rrule = new RRule(recur);
			deadline.getProperties().add(rrule);
		}

		if (event.getDescription() != null) {
			Description description = new Description(event.getDescription());
			deadline.getProperties().add(description);
		}

		// Generate a UID for the event..
		UidGenerator ug;

		ug = new RandomUidGenerator();
		deadline.getProperties().add(ug.generateUid());

		icalendar.getComponents().add(deadline);
		return icalendar;
	}

	private Calendar getIcal4jCalendarInstance() {
		Calendar icalendar = new Calendar();
		icalendar.getProperties().add(new ProdId("-//ResearchSpace//iCal4j 1.0//EN"));
		icalendar.getProperties().add(Version.VERSION_2_0);
		icalendar.getProperties().add(CalScale.GREGORIAN);
		return icalendar;
	}

}