package com.researchspace.booking.dao;

import com.researchspace.dao.query.RsqlCollectionQuery;
import com.researchspace.dao.query.RsqlCollectionQuery.Predicate;
import com.researchspace.dao.query.RsqlCollectionQuery.Subquery;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.inventory.ContainerLocation;
import com.researchspace.model.inventory.Instrument;
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
 * non-deleted stored Containers, Instruments or subsamples without role visibility. ContainerReadAccess supplies the shared container constraint; the Booking service supplies
 * the instrument read constraint.
 */
@Component
public class BookingLocationQuery {

  private final com.researchspace.service.inventory.ContainerReadAccess containers;

  public BookingLocationQuery(com.researchspace.service.inventory.ContainerReadAccess containers) {
    this.containers = containers;
  }

  private static final CollectionDescription<Instrument> INSTRUMENTS =
      com.researchspace.inventory.model.ApiV2InstrumentResource.DESCRIPTION;

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

    var access =
        containers.check(
            new com.researchspace.model.collection.AccessContext(
                caller,
                com.researchspace.model.collection.AccessContext.Operation.READ,
                "containers"));
    Predicate readable =
        new RsqlCollectionQuery(
                com.researchspace.inventory.model.ApiV2ContainerResource.DESCRIPTION,
                parent + ".container",
                name + "ParentRead")
            .translateTrusted(access.constraintOrEmpty().orElseThrow());
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
            readable);
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
}
