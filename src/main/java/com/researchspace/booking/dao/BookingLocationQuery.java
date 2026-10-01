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
import java.util.LinkedHashMap;
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
      return new Predicate("1 = 0", Map.of());
    }
    String target = name + "Target";
    String parent = name + "Parent";
    Map<String, Object> parameters = new LinkedHashMap<>();
    parameters.put(name + "TargetType", BookableTargetType.INSTRUMENT);
    String correlation =
        ".id = " + targetPath + ".id AND " + targetPath + ".type = :" + name + "TargetType";

    Predicate targetRead =
        new RsqlCollectionQuery(INSTRUMENTS, target, name + "TargetRead")
            .translateTrusted(readConstraint);
    parameters.putAll(targetRead.parameters());
    Subquery readableTarget =
        new Subquery(
            Instrument.class,
            target,
            target + correlation + " AND (" + targetRead.expression() + ")",
            targetRead.subqueries());

    Map<String, Subquery> children = new LinkedHashMap<>();
    Predicate direct =
        new RsqlCollectionQuery(CONTAINERS, parent + ".container", name + "Direct")
            .translateTrusted(readConstraint);
    parameters.putAll(direct.parameters());
    List<String> readable = new ArrayList<>(List.of("(" + direct.expression() + ")"));
    for (ChildKind kind : ChildKind.values()) {
      String child = name + kind.suffix;
      Predicate childRead =
          new RsqlCollectionQuery(kind.description, child + "." + kind.property, child + "Read")
              .translateTrusted(editConstraint);
      parameters.putAll(childRead.parameters());
      children.put(
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
              childRead.subqueries()));
      readable.add("EXISTS " + child);
    }
    String named = "";
    if (containerIds != null) {
      if (containerIds.isEmpty()) {
        // No parent is named, so nothing is stored in one.
        named = " AND 1 = 0";
      } else {
        parameters.put(name + "Containers", List.copyOf(containerIds));
        named = " AND " + parent + ".container.id IN :" + name + "Containers";
      }
    }
    String where =
        parent
            + ".storedInstrument"
            + correlation
            + named
            + " AND "
            + parent
            + ".container.deleted = false AND ("
            + String.join(" OR ", readable)
            + ")";
    return new Predicate(
        "EXISTS "
            + target
            + (stored ? " AND EXISTS " + parent : " AND NOT (EXISTS " + parent + ")"),
        parameters,
        Map.of(
            target,
            readableTarget,
            parent,
            new Subquery(ContainerLocation.class, parent, where, children)));
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
        List.of(),
        "id",
        List.of(new Sort("id", true)),
        AccessPolicy.readOnly(AccessFunction.authenticated()),
        readFilters);
  }
}
