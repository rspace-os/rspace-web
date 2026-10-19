package com.researchspace.booking.dao;

import static com.researchspace.inventory.model.InventoryReadFilters.OWNER_USERNAME;
import static com.researchspace.inventory.model.InventoryReadFilters.SHARING_ACL;
import static com.researchspace.inventory.model.InventoryReadFilters.SHARING_MODE;

import com.researchspace.dao.query.RsqlCollectionQuery;
import com.researchspace.dao.query.RsqlCollectionQuery.Predicate;
import com.researchspace.dao.query.RsqlCollectionQuery.Subquery;
import com.researchspace.inventory.model.InventoryReadFilters;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.InternalFilter;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.ContainerLocation;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.InventoryRecord.InventorySharingMode;
import com.researchspace.model.inventory.SubSample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Correlated SQL for the Booking location filter: whether a bookable item's immediate parent
 * Container or workbench is not deleted, is readable by the caller, and optionally is one of the
 * named Containers.
 *
 * <p>Readability is the readable-Container rule of {@code
 * InventoryDaoHibernate.readableContainerPredicate}, which decides every other location filter and
 * the catalogue's location member: the caller may read the Container itself, or one of its
 * non-deleted stored Containers, Instruments or subsamples without role visibility. The caller's
 * read and edit constraints are supplied by the Booking service and compile the same sharing facts;
 * the edit form is the read rule without role-visible owners.
 */
@Component
public class BookingLocationQuery {

  private static final List<InternalFilter> SUB_SAMPLE_READ_FILTERS =
      List.of(
          new InternalFilter(OWNER_USERNAME, "sample.owner.username", CollectionFieldTypes.text()),
          new InternalFilter(
              SHARING_MODE,
              "sample.sharingMode",
              CollectionFieldTypes.enumeration(InventorySharingMode.class)),
          new InternalFilter(SHARING_ACL, "sample.sharingACL.acl", CollectionFieldTypes.text()));

  private static final CollectionDescription<Container> CONTAINERS =
      readRuleDescription(Container.class, InventoryReadFilters.ALL);
  private static final CollectionDescription<Instrument> INSTRUMENTS =
      readRuleDescription(Instrument.class, InventoryReadFilters.ALL);
  private static final CollectionDescription<SubSample> SUB_SAMPLES =
      readRuleDescription(SubSample.class, SUB_SAMPLE_READ_FILTERS);

  /**
   * Whether the configuration target at {@code targetPath} is readable by {@code caller} and is
   * ({@code stored}) or is not stored directly in a non-deleted parent the caller may read.
   * Non-null {@code containerIds} consider only those parents; null considers any parent. This is
   * the complete meaning of every location comparison, so it needs no item list: {@code ==}/{@code
   * =in=} is {@code stored} in the named parents, {@code !=}/{@code =out=} is not, and {@code
   * =exists=} is either over any parent. Like the listed form, it never matches an unreadable
   * target, whichever way it is negated. {@code name} must be unique in the query; it names the
   * subqueries and parameters.
   */
  public Predicate readableParent(
      User caller,
      String targetPath,
      Set<Long> containerIds,
      boolean stored,
      String name,
      FilterExpression readConstraint,
      FilterExpression editConstraint) {
    if (caller == null || !caller.isEnabled() || caller.isAccountLocked()) {
      return new Predicate(RsqlCollectionQuery.FALSE_EXPRESSION, Map.of());
    }
    String target = name + "Target";
    String parent = name + "Parent";
    String correlation =
        ".id = " + targetPath + ".id AND " + targetPath + ".type = :" + name + "TargetType";

    Predicate targetRead =
        Predicate.and(
            new Predicate(
                target + correlation, Map.of(name + "TargetType", BookableTargetType.INSTRUMENT)),
            new RsqlCollectionQuery(INSTRUMENTS, target, name + "TargetRead")
                .translateTrusted(readConstraint));
    Predicate readableTarget =
        new Predicate(
            "EXISTS " + target,
            targetRead.parameters(),
            Map.of(
                target,
                new Subquery(
                    Instrument.class, target, targetRead.expression(), targetRead.subqueries())));

    List<Predicate> readable = new ArrayList<>();
    readable.add(
        new RsqlCollectionQuery(CONTAINERS, parent + ".container", name + "Direct")
            .translateTrusted(readConstraint));
    for (ChildKind kind : ChildKind.values()) {
      String child = name + kind.suffix;
      Predicate childRead =
          new RsqlCollectionQuery(kind.description, child + "." + kind.property, child + "Read")
              .translateTrusted(editConstraint);
      readable.add(
          new Predicate(
              "EXISTS " + child,
              childRead.parameters(),
              Map.of(
                  child,
                  new Subquery(
                      ContainerLocation.class,
                      child,
                      child
                          + ".container.id = "
                          + parent
                          + ".container.id AND ("
                          + childRead.expression()
                          + ")",
                      childRead.subqueries()))));
    }
    Predicate named = null;
    if (containerIds != null) {
      named =
          containerIds.isEmpty()
              ? new Predicate(RsqlCollectionQuery.FALSE_EXPRESSION, Map.of())
              : new Predicate(
                  parent + ".container.id IN :" + name + "Containers",
                  Map.of(name + "Containers", List.copyOf(containerIds)));
    }
    Predicate parentRead =
        Predicate.and(
            new Predicate(
                parent + ".storedInstrument" + correlation,
                Map.of(name + "TargetType", BookableTargetType.INSTRUMENT)),
            named,
            new Predicate(parent + ".container.deleted = false", Map.of()),
            Predicate.or(readable.toArray(Predicate[]::new)));
    return Predicate.and(
        readableTarget,
        new Predicate(
            stored ? "EXISTS " + parent : "NOT (EXISTS " + parent + ")",
            parentRead.parameters(),
            Map.of(
                parent,
                new Subquery(
                    ContainerLocation.class,
                    parent,
                    parentRead.expression(),
                    parentRead.subqueries()))));
  }

  private enum ChildKind {
    CONTAINER("ChildContainer", "storedContainer", CONTAINERS),
    INSTRUMENT("ChildInstrument", "storedInstrument", INSTRUMENTS),
    SUB_SAMPLE("ChildSubSample", "storedSubSample", SUB_SAMPLES);

    private final String suffix;
    private final String property;
    private final CollectionDescription<?> description;

    ChildKind(String suffix, String property, CollectionDescription<?> description) {
      this.suffix = suffix;
      this.property = property;
      this.description = description;
    }
  }

  /** The fields the Inventory read rule tests, and nothing a caller could filter on. */
  private static <T extends InventoryRecord> CollectionDescription<T> readRuleDescription(
      Class<T> type, List<InternalFilter> readFilters) {
    return new CollectionDescription<>(
        "booking-location-" + type.getSimpleName(),
        type,
        List.<Field<T, ?>>of(
            Field.<T, Long>readOnly("id", "id", CollectionFieldTypes.longNumber(), T::getId),
            Field.<T, Boolean>readOnly(
                "deleted", "deleted", CollectionFieldTypes.bool(), T::isDeleted)),
        "id",
        List.of(new Sort("id", true)),
        AccessPolicy.readOnly(AccessFunction.authenticated()),
        readFilters);
  }
}
