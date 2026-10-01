package com.researchspace.booking.service;

import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingEventKind;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.CommunicationNotifyPolicy;
import com.researchspace.service.NotificationConfig;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Optional;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Sends RSpace notifications for booking lifecycle events. */
@Service
public class BookingNotificationService {

  private static final String CREATED_MESSAGE_KEY = "bookingNotifications.created";
  private static final String CANCELLED_MESSAGE_KEY = "bookingNotifications.cancelled";

  private final InstrumentDao instrumentDao;
  private final CommunicationManager communicationManager;
  private final MessageSource messageSource;

  public BookingNotificationService(
      InstrumentDao instrumentDao,
      CommunicationManager communicationManager,
      MessageSource messageSource) {
    this.instrumentDao = instrumentDao;
    this.communicationManager = communicationManager;
    this.messageSource = messageSource;
  }

  /**
   * Notifies the instrument's current owner about a supported booking event.
   *
   * <p>This must run in the booking transaction so a notification cannot survive a failed booking
   * write.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void notify(TimeSlotBooking booking, User actor, NotificationType notificationType) {
    if (booking == null || booking.getKind() != BookingEventKind.BOOKING || actor == null) {
      return;
    }

    String messageKey = messageKey(notificationType);
    Optional<Instrument> instrument = currentInstrument(booking);
    if (instrument.isEmpty()) {
      return;
    }
    User owner = instrument.orElseThrow().getOwner();
    if (owner == null || actor.equals(owner) || !owner.wantsNotificationFor(notificationType)) {
      return;
    }

    NotificationConfig config = new NotificationConfig();
    config.setNotificationType(notificationType);
    config.setBroadcast(true);
    config.setPolicyOverride(CommunicationNotifyPolicy.ALWAYS_NOTIFY);
    config.setRecordAuthorisationRequired(false);
    config.setNotificationTargetsOverride(new HashSet<>(java.util.Set.of(owner)));

    communicationManager.notify(
        actor,
        null,
        config,
        messageSource.getMessage(
            messageKey,
            messageArguments(booking, instrument.orElseThrow()),
            org.springframework.context.i18n.LocaleContextHolder.getLocale()));
  }

  private Optional<Instrument> currentInstrument(TimeSlotBooking booking) {
    if (booking.getBookingConfiguration() == null) {
      return Optional.empty();
    }
    BookableTargetReference target = booking.getBookingConfiguration().getTarget();
    if (target == null || target.type() != BookableTargetType.INSTRUMENT || target.id() == null) {
      return Optional.empty();
    }
    return instrumentDao.getSafeNull(target.id()).filter(instrument -> !instrument.isDeleted());
  }

  private static String messageKey(NotificationType notificationType) {
    return switch (notificationType) {
      case NOTIFICATION_BOOKING_CREATED -> CREATED_MESSAGE_KEY;
      case NOTIFICATION_BOOKING_CANCELLED -> CANCELLED_MESSAGE_KEY;
      default ->
          throw new IllegalArgumentException(
              "Unsupported booking notification type: " + notificationType);
    };
  }

  private static Object[] messageArguments(TimeSlotBooking booking, Instrument instrument) {
    return new Object[] {
      StringEscapeUtils.escapeHtml4(booking.getId().toString()),
      StringEscapeUtils.escapeHtml4(instrument.getName()),
      StringEscapeUtils.escapeHtml4(instrument.getGlobalIdentifier()),
      DateTimeFormatter.ISO_INSTANT.format(booking.getStartTime().toInstant()),
      DateTimeFormatter.ISO_INSTANT.format(booking.getEndTime().toInstant())
    };
  }
}
