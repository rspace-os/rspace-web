package com.researchspace.model.booking;

import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.Field;
import com.researchspace.model.collection.CollectionDescription.InternalFilter;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.CollectionDescription.WriteOperation;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.RelationshipTarget;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.SplitReferenceBinding;
import com.researchspace.model.inventory.Instrument;
import java.util.List;
import java.util.Locale;

/** Stable REST API v2 shape for one-off time-slot bookings. */
public final class ApiV2TimeSlotBookingResource {

  private static final CollectionDescription.Relationship<TimeSlotBooking> TARGET =
      CollectionDescription.Relationship.polymorphicToOne(
              "target",
              CollectionFieldTypes.longNumber(),
              List.of(
                  new RelationshipTarget<>(
                      "instruments", BookableTargetType.INSTRUMENT, "IN", Instrument.class)),
              new SplitReferenceBinding<>(
                  ApiV2TimeSlotBookingResource::targetReference,
                  "bookingConfiguration.target.type",
                  "bookingConfiguration.target.id"))
          .writeOnlyOn(WriteOperation.CREATE)
          .required();

  public static final CollectionDescription<TimeSlotBooking> DESCRIPTION =
      new CollectionDescription<>(
          "bookings",
          TimeSlotBooking.class,
          List.<Field<TimeSlotBooking, ?>>of(
              Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), TimeSlotBooking::getId),
              Field.readOnly(
                      "timezone",
                      "timeZone",
                      CollectionFieldTypes.text(255),
                      TimeSlotBooking::getTimeZone)
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
          AccessPolicy.authenticated(),
          List.of(new InternalFilter("deleted", "deleted", CollectionFieldTypes.bool())));

  private ApiV2TimeSlotBookingResource() {}

  private static ResourceReference<BookableTargetType, Long> targetReference(
      TimeSlotBooking booking) {
    if (booking.getBookingConfiguration() == null
        || booking.getBookingConfiguration().getTarget() == null) {
      return null;
    }
    BookableTargetReference target = booking.getBookingConfiguration().getTarget();
    return new ResourceReference<>(target.type(), target.id());
  }
}
