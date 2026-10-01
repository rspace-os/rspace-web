package com.researchspace.model.booking;

import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.OpenApiSchemaDocumentation;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.Relationship;
import com.researchspace.model.collection.RelationshipTarget;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.collection.SplitReferenceBinding;
import java.util.List;
import java.util.function.Function;

/**
 * The immediate Inventory parent of a bookable item, as a Booking "Location" filter.
 *
 * <p>Location is filter-only: the parent is not a stored property of a Booking row, and its name is
 * visible only where the caller may read the Container. The Booking services therefore replace
 * every {@code location} comparison with a target comparison over the readable, configured items
 * stored directly in the named containers before the query compiles; see {@link #SELECTOR}. Nothing
 * renders the relationship, so responses carry {@code null}; {@code /api/v2/booking-catalogue}
 * returns each item's readable location.
 *
 * <p>The filter value is the Container's numeric ID behind the {@code IC} prefix. Workbenches share
 * the Container ID space and their {@code BE} global ID, so a workbench is filtered as {@code
 * IC<id>} too.
 */
public final class ApiV2BookingLocationResource {

  public static final String RESOURCE_NAME = "booking-locations";
  public static final String GLOBAL_ID_PREFIX = "IC";

  /** Public selector name, and the prefix of its {@code .value} ID selector. */
  public static final String SELECTOR = "location";

  /**
   * Property named by the unrewritten relationship. It does not exist, so a query path that skipped
   * the Booking rewrite fails instead of matching rows by an unrelated column.
   */
  static final String UNREWRITTEN_PROPERTY = "locationFilterRequiresBookingRewrite";

  /** Scalar view of one readable parent Container. */
  public record Location(Long id, String globalId, String name) {}

  public static final CollectionDescription<Location> DESCRIPTION =
      description(AccessFunction.authenticated());

  /** Builds the relationship-only projection; its loader applies Inventory read access. */
  public static CollectionDescription<Location> description(AccessFunction readAccess) {
    return new CollectionDescription<>(
        RESOURCE_NAME,
        Location.class,
        List.<Field<Location, ?>>of(
            Field.<Location, Long>readOnly(
                    "id", "id", CollectionFieldTypes.longNumber(), Location::id)
                .withQueryCapabilities(false, true)
                .documented(documentation("Location ID", "Container identifier.", "12")),
            Field.<Location, String>readOnly(
                    "globalId", "globalId", CollectionFieldTypes.text(), Location::globalId)
                .withQueryCapabilities(false, false)
                .documented(
                    documentation(
                        "Global ID", "Container or workbench global identifier.", "IC12")),
            Field.<Location, String>readOnly(
                    "name", "name", CollectionFieldTypes.text(255), Location::name)
                .withQueryCapabilities(false, false)
                .documented(documentation("Name", "Container name.", "Cold room"))),
        List.of(),
        "id",
        List.of(new Sort("id", true)),
        AccessPolicy.readOnly(readAccess));
  }

  /** The filter-only relationship that a Booking collection publishes as {@code location}. */
  public static <T> Relationship<T> relationship() {
    Function<T, ResourceReference<String, Long>> neverRendered = ignored -> null;
    return Relationship.polymorphicToOne(
            SELECTOR,
            CollectionFieldTypes.longNumber(),
            List.of(
                new RelationshipTarget<>(
                    RESOURCE_NAME, RESOURCE_NAME, GLOBAL_ID_PREFIX, Location.class)),
            SplitReferenceBinding.monomorphic(neverRendered, UNREWRITTEN_PROPERTY))
        .writeOnlyOn()
        .allowNull()
        .documented(
            new OpenApiSchemaDocumentation(
                "Location",
                "Filter-only: the bookable item's immediate Inventory parent Container or"
                    + " workbench, matched only where the caller may read it. Address a workbench"
                    + " as IC plus its ID. Responses render null; the booking catalogue returns"
                    + " each item's readable location.",
                null,
                null,
                null,
                null,
                List.of(),
                false));
  }

  /** Whether a comparison names the location relationship or its ID selector. */
  public static boolean isLocationSelector(String selector) {
    return SELECTOR.equals(selector) || (SELECTOR + ".value").equals(selector);
  }

  /** An always-false target comparison, for a location that names no readable item. */
  public static FilterExpression noTarget() {
    return new FilterExpression.Comparison("target.value", Operator.IN, List.of(-1L), false);
  }

  private static OpenApiSchemaDocumentation documentation(
      String title, String description, String example) {
    return new OpenApiSchemaDocumentation(
        title, description, example, null, null, null, List.of(), false);
  }

  private ApiV2BookingLocationResource() {}
}
