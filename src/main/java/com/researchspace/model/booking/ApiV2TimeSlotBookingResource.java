package com.researchspace.model.booking;

import com.researchspace.model.User;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldType;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.InternalFilter;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.Relationship;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.collection.WriteOperation;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/** Stable REST API v2 shape for one-off time-slot bookings. */
public final class ApiV2TimeSlotBookingResource {

  private static final CollectionFieldType<String> CALENDAR_OPTION =
      CollectionFieldTypes.text(
          Set.of(Operator.EQUAL, Operator.NOT_EQUAL, Operator.IN, Operator.NOT_IN, Operator.EXISTS),
          false);
  private static final CollectionFieldType<String> CALENDAR_PERSON =
      CollectionFieldTypes.text(Set.of(Operator.CONTAINS, Operator.LIKE, Operator.EXISTS), true);

  private static final Relationship<TimeSlotBooking> TARGET =
      ApiV2BookingConfigurationResource.instrumentTarget(
          ApiV2TimeSlotBookingResource::targetReference,
          "bookingConfiguration.target.type",
          "bookingConfiguration.target.id");

  public static final CollectionDescription<TimeSlotBooking> DESCRIPTION =
      description(AccessFunction.authenticated());

  /** Calendar-only query shape for privacy-safe derived event facets. */
  public static CollectionDescription<TimeSlotBooking> calendarFilterDescription(
      AccessFunction readAccess) {
    return new CollectionDescription<>(
        "bookings",
        TimeSlotBooking.class,
        List.of(
            Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), TimeSlotBooking::getId),
            Field.readOnly(
                    "timezone",
                    "bookingConfiguration.timeZone",
                    CollectionFieldTypes.text(255),
                    TimeSlotBooking::getVisibleTimeZone)
                .withQueryCapabilities(true, false),
            Field.readOnly(
                "purpose",
                "purpose",
                CollectionFieldTypes.text(1000),
                TimeSlotBooking::getVisiblePurpose),
            Field.<TimeSlotBooking, Long>readOnly(
                    "requesterId",
                    "requester.id",
                    CollectionFieldTypes.longNumber(),
                    booking -> visibleRequester(booking, User::getId))
                .withQueryCapabilities(true, false),
            Field.readOnly(
                "bookedBy",
                "requester.username",
                CALENDAR_PERSON,
                TimeSlotBooking::getVisibleBookedBy),
            Field.<TimeSlotBooking, String>readOnly(
                    "requesterUsername",
                    "requester.username",
                    CollectionFieldTypes.text(255),
                    booking -> visibleRequester(booking, User::getUsername))
                .withQueryCapabilities(true, false),
            Field.<TimeSlotBooking, String>readOnly(
                    "requesterFirstName",
                    "requester.firstName",
                    CollectionFieldTypes.text(255),
                    booking -> visibleRequester(booking, User::getFirstName))
                .withQueryCapabilities(true, false),
            Field.<TimeSlotBooking, String>readOnly(
                    "requesterLastName",
                    "requester.lastName",
                    CollectionFieldTypes.text(255),
                    booking -> visibleRequester(booking, User::getLastName))
                .withQueryCapabilities(true, false),
            Field.<TimeSlotBooking, String>readOnly(
                    "privacy",
                    "id",
                    CALENDAR_OPTION,
                    booking -> booking.getPrivacy().name().toLowerCase(Locale.ROOT))
                .withQueryCapabilities(true, false),
            Field.readOnly(
                "kind",
                "kind",
                CollectionFieldTypes.enumeration(BookingEventKind.class),
                TimeSlotBooking::getKind),
            Field.readOnly(
                "start",
                "startTime",
                CollectionFieldTypes.instant(),
                TimeSlotBooking::getStartTime),
            Field.readOnly(
                "end", "endTime", CollectionFieldTypes.instant(), TimeSlotBooking::getEndTime),
            TARGET,
            ApiV2BookingLocationResource.<TimeSlotBooking>relationship()),
        "id",
        List.of(new Sort("id", true)),
        AccessPolicy.readOnly(readAccess));
  }

  /** Builds the booking collection with its server-owned event visibility predicate. */
  public static CollectionDescription<TimeSlotBooking> description(AccessFunction readAccess) {
    return new CollectionDescription<>(
        "bookings",
        TimeSlotBooking.class,
        List.of(
            Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), TimeSlotBooking::getId),
            Field.readOnly(
                    "version",
                    "version",
                    CollectionFieldTypes.longNumber(),
                    TimeSlotBooking::getVersion)
                .withQueryCapabilities(false, false),
            Field.readOnly(
                    "timezone",
                    "timeZone",
                    CollectionFieldTypes.text(255),
                    TimeSlotBooking::getVisibleTimeZone)
                .withQueryCapabilities(false, false),
            Field.<TimeSlotBooking, Long>readOnly(
                    "requesterId",
                    "requester.id",
                    CollectionFieldTypes.longNumber(),
                    booking -> visibleRequester(booking, User::getId))
                .allowNull()
                .withQueryCapabilities(true, false),
            Field.writable(
                    "kind",
                    "kind",
                    CollectionFieldTypes.enumeration(BookingEventKind.class),
                    TimeSlotBooking::getKind,
                    TimeSlotBooking::setKind)
                .writeOnlyOn(WriteOperation.CREATE),
            Field.writable(
                    "start",
                    "startTime",
                    CollectionFieldTypes.instant(),
                    TimeSlotBooking::getStartTime,
                    TimeSlotBooking::setStartTime)
                .required(),
            Field.writable(
                    "end",
                    "endTime",
                    CollectionFieldTypes.instant(),
                    TimeSlotBooking::getEndTime,
                    TimeSlotBooking::setEndTime)
                .required(),
            Field.writable(
                    "state",
                    "state",
                    CollectionFieldTypes.enumeration(BookingState.class),
                    TimeSlotBooking::getState,
                    TimeSlotBooking::setState)
                .writeOnlyOn(WriteOperation.UPDATE),
            Field.writable(
                    "cancellationReason",
                    "cancellationReason",
                    CollectionFieldTypes.text(500),
                    TimeSlotBooking::getVisibleCancellationReason,
                    TimeSlotBooking::setCancellationReason)
                .allowNull()
                .writeOnlyOn(WriteOperation.UPDATE)
                .withQueryCapabilities(false, false),
            Field.writable(
                    "purpose",
                    "purpose",
                    CollectionFieldTypes.text(1000),
                    TimeSlotBooking::getVisiblePurpose,
                    TimeSlotBooking::setPurpose)
                .allowNull()
                .withQueryCapabilities(true, false),
            Field.readOnly(
                    "bookedBy",
                    "visibleBookedBy",
                    CollectionFieldTypes.text(),
                    TimeSlotBooking::getVisibleBookedBy)
                .allowNull()
                .withQueryCapabilities(false, false),
            Field.readOnly(
                    "createdBy",
                    "visibleCreatedBy",
                    CollectionFieldTypes.text(),
                    TimeSlotBooking::getVisibleCreatedBy)
                .allowNull()
                .withQueryCapabilities(false, false),
            Field.<TimeSlotBooking, String>readOnly(
                    "privacy",
                    "privacy",
                    CollectionFieldTypes.text(),
                    booking -> booking.getPrivacy().name().toLowerCase(Locale.ROOT))
                .withQueryCapabilities(false, false),
            Field.readOnly(
                    "canEdit", "canEdit", CollectionFieldTypes.bool(), TimeSlotBooking::isCanEdit)
                .withQueryCapabilities(false, false),
            Field.readOnly(
                    "canCancel",
                    "canCancel",
                    CollectionFieldTypes.bool(),
                    TimeSlotBooking::isCanCancel)
                .withQueryCapabilities(false, false),
            Field.readOnly(
                    "canViewConfiguration",
                    "canViewConfiguration",
                    CollectionFieldTypes.bool(),
                    TimeSlotBooking::isCanViewConfiguration)
                .withQueryCapabilities(false, false),
            Field.readOnly(
                "createdAt",
                "createdAt",
                CollectionFieldTypes.instant(),
                TimeSlotBooking::getCreatedAt),
            Field.readOnly(
                "updatedAt",
                "updatedAt",
                CollectionFieldTypes.instant(),
                TimeSlotBooking::getUpdatedAt),
            TARGET),
        "id",
        List.of(new Sort("start", true), new Sort("id", true)),
        new AccessPolicy(
            readAccess,
            AccessFunction.authenticated(),
            AccessFunction.authenticated(),
            AccessFunction.authenticated(),
            AccessFunction.authenticated()),
        List.of(new InternalFilter("deleted", "deleted", CollectionFieldTypes.bool())));
  }

  private ApiV2TimeSlotBookingResource() {}

  /**
   * Reads a requester value only in full detail, like {@code bookedBy}: a busy event names nobody.
   * Filters on these fields are limited to the same events by {@link BookingRequesterFilters}.
   */
  private static <V> V visibleRequester(TimeSlotBooking booking, Function<User, V> value) {
    User requester = booking.getPrivacy() == BookingPrivacy.FULL ? booking.getRequester() : null;
    return requester == null ? null : value.apply(requester);
  }

  private static ResourceReference<BookableTargetType, Long> targetReference(
      TimeSlotBooking booking) {
    BookableTargetReference target = booking.getVisibleTarget();
    if (target == null) {
      return null;
    }
    return new ResourceReference<>(target.type(), target.id());
  }
}
