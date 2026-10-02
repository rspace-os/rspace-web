package com.researchspace.booking.service;

import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingEventKind;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.comms.data.BookingNotificationData;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.CommunicationNotifyPolicy;
import com.researchspace.service.NotificationConfig;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Sends RSpace notifications for booking lifecycle events. */
@Service
public class BookingNotificationService {

  private final InstrumentDao instrumentDao;
  private final CommunicationManager communicationManager;
  private final BookingNotificationRecipientReader recipientReader;
  private final BookingNotificationMessageFormatter messageFormatter;

  public BookingNotificationService(
      InstrumentDao instrumentDao,
      CommunicationManager communicationManager,
      BookingNotificationRecipientReader recipientReader,
      BookingNotificationMessageFormatter messageFormatter) {
    this.instrumentDao = instrumentDao;
    this.communicationManager = communicationManager;
    this.recipientReader = recipientReader;
    this.messageFormatter = messageFormatter;
  }

  /**
   * Notifies each eligible subscriber about a supported booking event.
   *
   * <p>This must run in the booking transaction so a notification cannot survive a failed booking
   * write.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void notify(TimeSlotBooking booking, User actor, NotificationType notificationType) {
    if (booking == null || booking.getKind() != BookingEventKind.BOOKING || actor == null) {
      return;
    }

    Optional<Instrument> instrument = currentInstrument(booking);
    if (instrument.isEmpty()) {
      return;
    }
    Instrument targetInstrument = instrument.orElseThrow();
    BookingNotificationData data = notificationData(booking, targetInstrument);
    for (BookingNotificationRecipient recipient :
        recipientReader.selectRecipients(
            targetInstrument.getId(), notificationType, actor.getId())) {
      NotificationConfig config = new NotificationConfig();
      config.setNotificationType(notificationType);
      config.setBroadcast(true);
      config.setPolicyOverride(CommunicationNotifyPolicy.ALWAYS_NOTIFY);
      config.setRecordAuthorisationRequired(false);
      config.setNotificationTargetsOverride(new java.util.HashSet<>(Set.of(recipient.user())));
      config.setNotificationData(data);

      communicationManager.notify(
          actor,
          null,
          config,
          messageFormatter.format(
              notificationType, data, recipient.displayZone(), LocaleContextHolder.getLocale()));
    }
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

  private static BookingNotificationData notificationData(
      TimeSlotBooking booking, Instrument instrument) {
    BookingNotificationData data = new BookingNotificationData();
    data.setBookingId(booking.getId().toString());
    data.setInstrumentName(instrument.getName());
    data.setInstrumentGlobalIdentifier(instrument.getGlobalIdentifier());
    data.setStartTime(DateTimeFormatter.ISO_INSTANT.format(booking.getStartTime().toInstant()));
    data.setEndTime(DateTimeFormatter.ISO_INSTANT.format(booking.getEndTime().toInstant()));
    return data;
  }
}
