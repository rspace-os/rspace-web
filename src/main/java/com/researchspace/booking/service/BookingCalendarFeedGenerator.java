package com.researchspace.booking.service;

import com.researchspace.booking.service.TimeSlotBookingManager.CalendarEvent;
import com.researchspace.booking.service.TimeSlotBookingManager.CalendarSource;
import com.researchspace.model.booking.BookingEventKind;
import com.researchspace.model.booking.BookingPrivacy;
import com.researchspace.service.MessageSourceUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStamp;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.LastModified;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Transp;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Url;
import net.fortuna.ical4j.model.property.XProperty;
import net.fortuna.ical4j.model.property.immutable.ImmutableCalScale;
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion;
import net.fortuna.ical4j.validate.ValidationException;
import org.springframework.stereotype.Component;

/** Deterministically serializes privacy-shaped booking data as iCalendar. */
@Component
public final class BookingCalendarFeedGenerator {

  private static final String PROD_ID = "-//ResearchSpace//Booking Calendar//EN";
  private final MessageSourceUtils messages;

  public BookingCalendarFeedGenerator(MessageSourceUtils messages) {
    this.messages = messages;
  }

  /** Builds and validates one complete UTF-8 calendar within the supplied byte limit. */
  public byte[] generate(CalendarSource source, URI serverBaseUrl, Locale locale, int maxBytes) {
    PropertyList properties =
        new PropertyList()
            .add(new ProdId(PROD_ID))
            .add(ImmutableVersion.VERSION_2_0)
            .add(ImmutableCalScale.GREGORIAN)
            .add(
                new XProperty(
                    "X-WR-CALNAME",
                    source.translateName()
                        ? messages.getMessageForLocale(source.itemName(), locale)
                        : source.itemName()))
            .add(new XProperty("X-WR-TIMEZONE", source.timeZone()));
    ComponentList<VEvent> events = new ComponentList<>();
    for (CalendarEvent event : source.events()) {
      events = events.add(event(event, serverBaseUrl, locale));
    }
    Calendar calendar = new Calendar(properties, events);
    if (calendar.validate().hasErrors()) {
      throw new CalendarGenerationException("Generated calendar failed validation");
    }

    ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.min(maxBytes, 16_384));
    try (OutputStream limited = new LimitedOutputStream(bytes, maxBytes)) {
      new CalendarOutputter().output(calendar, limited);
    } catch (CalendarTooLargeException ex) {
      throw ex;
    } catch (IOException | ValidationException ex) {
      throw new CalendarGenerationException("Could not serialize booking calendar", ex);
    }
    return bytes.toByteArray();
  }

  private VEvent event(CalendarEvent source, URI serverBaseUrl, Locale locale) {
    Instant stamp = timestamp(source);
    boolean full = source.privacy() == BookingPrivacy.FULL;
    String summary =
        messages.getMessageForLocale(
            source.kind() == BookingEventKind.MAINTENANCE
                ? "booking:calendar.feed.maintenance"
                : full ? "booking:calendar.feed.booked" : "booking:calendar.feed.busy",
            locale);
    if (source.itemName() != null && !source.itemName().isBlank()) {
      summary =
          messages.getMessage(
              "booking:calendar.feed.itemSummary",
              new Object[] {source.itemName(), summary},
              locale);
    }
    PropertyList properties =
        new PropertyList()
            .add(new Uid("booking-" + source.id() + "@" + serverBaseUrl.getHost()))
            .add(new DtStamp(stamp))
            .add(new DtStart<>(source.start().toInstant()))
            .add(new DtEnd<>(source.end().toInstant()))
            .add(new Summary(summary));
    if (full) {
      List<String> description = new ArrayList<>();
      description.add(
          messages.getMessage(
              source.kind() == BookingEventKind.MAINTENANCE
                  ? "booking:calendar.feed.createdBy"
                  : "booking:calendar.feed.bookedBy",
              new Object[] {
                source.kind() == BookingEventKind.MAINTENANCE
                    ? source.createdBy()
                    : source.bookedBy()
              },
              locale));
      if (source.purpose() != null && !source.purpose().isBlank()) {
        description.add(
            messages.getMessage(
                "booking:calendar.feed.purpose", new Object[] {source.purpose()}, locale));
      }
      properties = properties.add(new Description(String.join("\n", description)));
      if (source.canEdit()) {
        properties =
            properties.add(
                new Url(appendPath(serverBaseUrl, "/booking/calendar/bookings/" + source.id())));
      }
    }
    if (source.updatedAt() != null) {
      properties = properties.add(new LastModified(source.updatedAt().toInstant()));
    }
    properties =
        properties.add(new Status(Status.VALUE_CONFIRMED)).add(new Transp(Transp.VALUE_OPAQUE));
    return new VEvent(properties);
  }

  private static Instant timestamp(CalendarEvent event) {
    Date value =
        event.updatedAt() != null
            ? event.updatedAt()
            : event.createdAt() != null ? event.createdAt() : event.start();
    return value.toInstant();
  }

  static URI appendPath(URI base, String path) {
    String value = base.toString();
    return URI.create(
        (value.endsWith("/") ? value.substring(0, value.length() - 1) : value) + path);
  }

  /** Signals that the serialized UTF-8 calendar would exceed its configured limit. */
  public static final class CalendarTooLargeException extends RuntimeException {

    private static final long serialVersionUID = 1L;
  }

  /** Signals an invalid or otherwise unserializable calendar. */
  public static final class CalendarGenerationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    CalendarGenerationException(String message) {
      super(message);
    }

    CalendarGenerationException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private static final class LimitedOutputStream extends OutputStream {

    private final OutputStream delegate;
    private final int maximum;
    private int count;

    private LimitedOutputStream(OutputStream delegate, int maximum) {
      if (maximum < 1) {
        throw new IllegalArgumentException("Calendar byte limit must be positive");
      }
      this.delegate = delegate;
      this.maximum = maximum;
    }

    @Override
    public void write(int value) throws IOException {
      requireCapacity(1);
      delegate.write(value);
      count++;
    }

    @Override
    public void write(byte[] values, int offset, int length) throws IOException {
      requireCapacity(length);
      delegate.write(values, offset, length);
      count += length;
    }

    private void requireCapacity(int length) {
      if (length > maximum - count) {
        throw new CalendarTooLargeException();
      }
    }
  }
}
