package com.researchspace.model.booking;

import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldType;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.InternalFilter;
import com.researchspace.model.collection.Relationship;
import com.researchspace.model.collection.RelationshipTarget;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.collection.SplitReferenceBinding;
import com.researchspace.model.collection.WriteOperation;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Stable REST API v2 shape for one-off time-slot bookings. */
public final class ApiV2TimeSlotBookingResource {

  private static final CollectionFieldType<String> CALENDAR_OPTION =
      new CollectionFieldType<>() {
        @Override
        public Class<String> javaType() {
          return String.class;
        }

        @Override
        public InputKind inputKind() {
          return InputKind.STRING;
        }

        @Override
        public String parse(String value) {
          return value;
        }

        @Override
        public Object serialize(String value) {
          return value;
        }

        @Override
        public Set<com.researchspace.model.collection.Operator> operators() {
          return Set.of(
              com.researchspace.model.collection.Operator.EQUAL,
              com.researchspace.model.collection.Operator.NOT_EQUAL,
              com.researchspace.model.collection.Operator.IN,
              com.researchspace.model.collection.Operator.NOT_IN,
              com.researchspace.model.collection.Operator.EXISTS);
        }

        @Override
        public boolean sortable() {
          return false;
        }
      };

  private static final CollectionFieldType<String> CALENDAR_PERSON =
      new CollectionFieldType<>() {
        @Override
        public Class<String> javaType() {
          return String.class;
        }

        @Override
        public InputKind inputKind() {
          return InputKind.STRING;
        }

        @Override
        public String parse(String value) {
          return value;
        }

        @Override
        public Object serialize(String value) {
          return value;
        }

        @Override
        public Set<com.researchspace.model.collection.Operator> operators() {
          return Set.of(
              com.researchspace.model.collection.Operator.CONTAINS,
              com.researchspace.model.collection.Operator.LIKE,
              com.researchspace.model.collection.Operator.EXISTS);
        }

        @Override
        public boolean supportsWildcards() {
          return true;
        }

        @Override
        public boolean sortable() {
          return false;
        }
      };

  private static final Relationship<TimeSlotBooking> TARGET =
      Relationship.polymorphicToOne(
              "target",
              CollectionFieldTypes.longNumber(),
              List.of(
                  new RelationshipTarget<>(
                      ApiV2BookingInstrumentResource.RESOURCE_NAME,
                      BookableTargetType.INSTRUMENT,
                      "IN",
                      com.researchspace.model.inventory.Instrument.class)),
              new SplitReferenceBinding<>(
                  ApiV2TimeSlotBookingResource::targetReference,
                  "bookingConfiguration.target.type",
                  "bookingConfiguration.target.id"))
          .writeOnlyOn(WriteOperation.CREATE)
          .required();

  public static final CollectionDescription<TimeSlotBooking> DESCRIPTION =
      description(AccessFunction.authenticated());

  /** Calendar-only query shape for privacy-safe derived event facets. */
  public static CollectionDescription<TimeSlotBooking> calendarFilterDescription(
      AccessFunction readAccess) {
    return new CollectionDescription<>(
        "bookings",
        TimeSlotBooking.class,
        List.<Field<TimeSlotBooking, ?>>of(
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
                    booking -> booking.getRequester().getId())
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
                    booking -> booking.getRequester().getUsername())
                .withQueryCapabilities(true, false),
            Field.<TimeSlotBooking, String>readOnly(
                    "requesterFirstName",
                    "requester.firstName",
                    CollectionFieldTypes.text(255),
                    booking -> booking.getRequester().getFirstName())
                .withQueryCapabilities(true, false),
            Field.<TimeSlotBooking, String>readOnly(
                    "requesterLastName",
                    "requester.lastName",
                    CollectionFieldTypes.text(255),
                    booking -> booking.getRequester().getLastName())
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
                "end", "endTime", CollectionFieldTypes.instant(), TimeSlotBooking::getEndTime)),
        List.of(TARGET),
        "id",
        List.of(new Sort("id", true)),
        AccessPolicy.readOnly(readAccess));
  }

  /** Builds the booking collection with its server-owned event visibility predicate. */
  public static CollectionDescription<TimeSlotBooking> description(AccessFunction readAccess) {
    return new CollectionDescription<>(
        "bookings",
        TimeSlotBooking.class,
        List.<Field<TimeSlotBooking, ?>>of(
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
                    booking -> booking.getRequester().getId())
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
                    "purpose",
                    "purpose",
                    CollectionFieldTypes.text(1000),
                    TimeSlotBooking::getVisiblePurpose,
                    TimeSlotBooking::setPurpose)
                .allowNull()
                .withQueryCapabilities(false, false),
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
                TimeSlotBooking::getUpdatedAt)),
        List.of(TARGET),
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

  private static ResourceReference<BookableTargetType, Long> targetReference(
      TimeSlotBooking booking) {
    BookableTargetReference target = booking.getVisibleTarget();
    if (target == null) {
      return null;
    }
    return new ResourceReference<>(target.type(), target.id());
  }
}
