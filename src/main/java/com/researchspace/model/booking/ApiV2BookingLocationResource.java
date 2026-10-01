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
 * The immediate Inventory parent of a bookable item, as a Booking "Location" relationship.
 *
 * <p>The parent is not a stored property of a Booking row, and its name is visible only where the
 * caller may read the Container. The Booking services therefore replace every {@code location}
 * comparison with a target comparison over the readable, configured items stored directly in the
 * named containers before the query compiles; see {@link #SELECTOR}. Booking configurations never
 * render it; bookings render it from a per-caller readable lookup (see {@link
 * #renderedRelationship}), and {@code /api/v2/booking-catalogue} returns each item's location.
 *
 * <p>The filter value is the Container's numeric ID behind the {@code IC} prefix. Workbenches share
 * the Container ID space and their {@code BE} global ID, so a workbench is filtered as {@code
 * IC<id>} too.
 */
public final class ApiV2BookingLocationResource {

  public static final String RESOURCE_NAME = "containers";
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

  /** The filter-only relationship that a Booking collection publishes as {@code location}. */
  public static <T> Relationship<T> relationship() {
    return relationship(
        ignored -> null,
        "Filter-only: the bookable item's immediate Inventory parent Container or workbench,"
            + " matched only where the caller may read it. Address a workbench as IC plus its ID."
            + " Responses render null; the booking catalogue returns each item's readable"
            + " location.");
  }

  /**
   * The {@code location} relationship a booking response renders: the bookable item's immediate
   * parent, prepared per caller, or null where the caller may not read it. Filters are rewritten
   * exactly as for {@link #relationship()}.
   */
  public static <T> Relationship<T> renderedRelationship(Function<T, Location> preparedLocation) {
    return relationship(
        row -> {
          Location location = preparedLocation.apply(row);
          return location == null ? null : new ResourceReference<>(RESOURCE_NAME, location.id());
        },
        "The bookable item's immediate Inventory parent Container or workbench, rendered and"
            + " matched only where the caller may read it; otherwise null. Address a workbench as"
            + " IC plus its ID.");
  }

  private static <T> Relationship<T> relationship(
      Function<T, ResourceReference<String, Long>> reference, String description) {
    return Relationship.polymorphicToOne(
            SELECTOR,
            CollectionFieldTypes.longNumber(),
            List.of(
                new RelationshipTarget<>(
                    RESOURCE_NAME, RESOURCE_NAME, GLOBAL_ID_PREFIX, com.researchspace.model.inventory.Container.class)),
            SplitReferenceBinding.monomorphic(reference, UNREWRITTEN_PROPERTY))
        .writeOnlyOn()
        .allowNull()
        .documented(OpenApiSchemaDocumentation.of("Location", description, null));
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
    return OpenApiSchemaDocumentation.of(title, description, example);
  }

  private ApiV2BookingLocationResource() {}
}
