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
import java.util.List;
import java.util.Objects;
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
    notify(booking, actor, notificationType, false);
  }

  /**
   * Notifies subscribers that a cancelled booking was restored to its slot. It uses the "booking
   * created" type, so the same subscribers and preferences apply, with a "restored" message.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void notifyRestored(TimeSlotBooking booking, User actor) {
    notify(booking, actor, NotificationType.NOTIFICATION_BOOKING_CREATED, true);
  }

  private void notify(
      TimeSlotBooking booking, User actor, NotificationType notificationType, boolean restored) {
    if (booking == null || booking.getKind() != BookingEventKind.BOOKING || actor == null) {
      return;
    }

    Optional<Instrument> instrument = currentInstrument(booking);
    if (instrument.isEmpty()) {
      return;
    }
    Instrument targetInstrument = instrument.orElseThrow();
    BookingNotificationData data = notificationData(booking, targetInstrument);
    data.setRestored(restored);
    Long requesterId = booking.getRequester() == null ? null : booking.getRequester().getId();
    List<BookingNotificationRecipient> recipients =
        NotificationType.NOTIFICATION_BOOKING_CANCELLED.equals(notificationType)
            ? recipientReader.selectRecipients(
                targetInstrument.getId(), notificationType, actor.getId(), requesterId)
            : recipientReader.selectRecipients(
                targetInstrument.getId(), notificationType, actor.getId());
    for (BookingNotificationRecipient recipient : recipients) {
      NotificationConfig config = new NotificationConfig();
      config.setNotificationType(notificationType);
      config.setBroadcast(true);
      config.setPolicyOverride(CommunicationNotifyPolicy.ALWAYS_NOTIFY);
      config.setRecordAuthorisationRequired(false);
      config.setNotificationTargetsOverride(new java.util.HashSet<>(Set.of(recipient.user())));
      config.setNotificationData(data);
      config.setNotificationEventPreferenceOverride(
          NotificationType.NOTIFICATION_BOOKING_CANCELLED.equals(notificationType)
              && requesterId != null
              && !Objects.equals(actor.getId(), requesterId)
              && Objects.equals(recipient.user().getId(), requesterId));

      // The saved message is the email body, so its links must be absolute. In-app lists
      // re-format the structured data with root-relative links at display time.
      communicationManager.notify(
          actor,
          null,
          config,
          messageFormatter.formatForEmail(
              notificationType,
              data,
              recipient.displayZone(),
              LocaleContextHolder.getLocale(),
              recipient.timeFormat()));
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
    data.setCancellationReason(booking.getCancellationReason());
    return data;
  }
}
