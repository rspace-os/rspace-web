package com.researchspace.dao.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.dao.query.RsqlCollectionQuery.Predicate;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.inventory.Instrument;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Compiles a filter on a field reached through a relationship. */
class RelationshipTargetFilterTest {

  private record TwoTargets(Long id, Instrument primary, Instrument secondary) {}

  private static final FilterExpression ACTIVE_ONLY =
      new FilterExpression.Comparison("deleted", Operator.EQUAL, List.of(false), false);

  private final RsqlCollectionQuery query =
      new RsqlCollectionQuery(ApiV2BookingConfigurationResource.DESCRIPTION, "cfg", "rsql");

  private static RelationshipReadAccess targets(FilterExpression access) {
    AccessFunction readAccess =
        AccessFunction.documented(
            "Test relationship target access.",
            Set.of(),
            ignored -> access == null ? AccessResult.allowed() : AccessResult.allowedWhere(access));
    ResourceRegistry registry =
        new ResourceRegistry(
            List.of(
                ApiV2BookingConfigurationResource.DESCRIPTION,
                ApiV2UserResource.DESCRIPTION,
                ApiV2InstrumentResource.description(readAccess)));
    return RelationshipReadAccess.forActor(registry, null);
  }

  private static FilterExpression byTargetName(Operator operator, Object value) {
    return new FilterExpression.Comparison("target.name", operator, List.of(value), false);
  }

  @Test
  void compilesToASubqueryCorrelatedOnTheStoredReference() {
    Predicate compiled =
        query.translate(byTargetName(Operator.CONTAINS, "confocal"), targets(null));

    assertEquals(1, compiled.subqueries().size());
    RsqlCollectionQuery.Subquery subquery = compiled.subqueries().values().iterator().next();

    assertTrue(
        compiled.expression().contains("EXISTS " + subquery.alias() + "Sub"),
        "the subquery is referenced by name, because Blaze parses none inside an expression");
    assertEquals(Instrument.class, subquery.entityType());
    assertTrue(
        subquery.whereExpression().contains(" = cfg.target.id"),
        "the subquery must correlate on the stored target id, not on a join");
    assertTrue(
        subquery.whereExpression().contains("cfg.target.type = :"),
        "a polymorphic reference must also pin the stored kind");
    assertTrue(compiled.parameters().containsValue("%confocal%"));
  }

  @Test
  void repeatsTheReadRuleOfTheTargetInsideTheSubquery() {
    Predicate withRule =
        query.translate(byTargetName(Operator.CONTAINS, "confocal"), targets(ACTIVE_ONLY));
    Predicate withoutRule =
        query.translate(byTargetName(Operator.CONTAINS, "confocal"), targets(null));

    assertTrue(
        where(withRule).contains(".deleted = :"),
        "omitting the target's read rule would match rows whose target the caller cannot read");
    assertFalse(where(withoutRule).contains(".deleted = :"));
    assertTrue(withRule.parameters().containsValue(false));
  }

  @Test
  void appliesTargetAccessWhenFilteringOnTheStoredRelationshipId() {
    FilterExpression byId =
        new FilterExpression.Comparison("target.value", Operator.EQUAL, List.of(7L), false);

    Predicate compiled = query.translate(byId, targets(ACTIVE_ONLY));

    assertEquals(1, compiled.subqueries().size());
    assertTrue(compiled.expression().contains("EXISTS"));
    assertTrue(compiled.expression().contains("cfg.target.id = :"));
    assertTrue(where(compiled).contains(".deleted = :"));
    assertTrue(compiled.parameters().containsValue(7L));
  }

  @Test
  void keepsSubqueryParametersDistinctFromTheOuterFilter() {
    Predicate compiled =
        query.translate(
            new FilterExpression.And(
                List.of(
                    new FilterExpression.Comparison(
                        "enabled", Operator.EQUAL, List.of(true), false),
                    byTargetName(Operator.CONTAINS, "confocal"))),
            targets(ACTIVE_ONLY));

    assertEquals(
        compiled.parameters().size(),
        compiled.parameters().keySet().size(),
        "a reused parameter name would silently overwrite a value and change which rows match");
    assertTrue(compiled.parameters().containsValue(true));
    assertTrue(compiled.parameters().containsValue(false));
    assertTrue(compiled.parameters().containsValue("%confocal%"));
  }

  private static String where(Predicate predicate) {
    return predicate.subqueries().values().iterator().next().whereExpression();
  }

  @Test
  void refusesANegativeOperatorOnARelationshipField() {
    assertThrows(
        CollectionQueryException.class,
        () -> query.translate(byTargetName(Operator.NOT_EQUAL, "confocal"), targets(null)));
  }

  @Test
  void refusesAFieldTheTargetDoesNotPublishAsFilterable() {
    assertThrows(
        CollectionQueryException.class,
        () ->
            query.translate(
                new FilterExpression.Comparison(
                    "target.globalId", Operator.EQUAL, List.of("IN1"), false),
                targets(null)),
        "globalId is described with withQueryCapabilities(false, false)");
    assertThrows(
        CollectionQueryException.class,
        () -> query.translate(byTargetName(Operator.EQUAL, "x"), RelationshipReadAccess.none()),
        "with no target descriptions there is nothing to filter through");
  }

  @Test
  void refusesAFieldOfSomethingThatIsNotARelationship() {
    assertThrows(
        CollectionQueryException.class,
        () ->
            query.translate(
                new FilterExpression.Comparison(
                    "enabled.name", Operator.EQUAL, List.of("x"), false),
                targets(null)));
  }

  @Test
  void appliesOneCachedDestinationPolicyToEachFilteredRelationshipAlias() {
    AtomicInteger evaluations = new AtomicInteger();
    AccessFunction readAccess =
        AccessFunction.documented(
            "Test relationship target access.",
            Set.of(),
            ignored -> {
              evaluations.incrementAndGet();
              return AccessResult.allowedWhere(ACTIVE_ONLY);
            });
    CollectionDescription.Relationship<TwoTargets> primary =
        CollectionDescription.Relationship.referenceToOne(
            "primary",
            "instruments",
            CollectionFieldTypes.longNumber(),
            Instrument.class,
            TwoTargets::primary,
            Instrument::getId,
            "primaryId");
    CollectionDescription.Relationship<TwoTargets> secondary =
        CollectionDescription.Relationship.referenceToOne(
            "secondary",
            "instruments",
            CollectionFieldTypes.longNumber(),
            Instrument.class,
            TwoTargets::secondary,
            Instrument::getId,
            "secondaryId");
    CollectionDescription<TwoTargets> source =
        new CollectionDescription<>(
            "two-targets",
            TwoTargets.class,
            List.of(
                CollectionDescription.Field.readOnly(
                    "id", "id", CollectionFieldTypes.longNumber(), TwoTargets::id)),
            List.of(primary, secondary),
            "id",
            List.of(new Sort("id", true)));
    ResourceRegistry registry =
        new ResourceRegistry(List.of(source, ApiV2InstrumentResource.description(readAccess)));

    Predicate compiled =
        new RsqlCollectionQuery(source, "source", "relationshipFilter")
            .translate(
                new FilterExpression.And(
                    List.of(
                        new FilterExpression.Comparison(
                            "primary.name", Operator.CONTAINS, List.of("alpha"), false),
                        new FilterExpression.Comparison(
                            "secondary.name", Operator.CONTAINS, List.of("beta"), false))),
                RelationshipReadAccess.forActor(registry, null));

    assertEquals(1, evaluations.get(), "one destination policy evaluation per resource and actor");
    assertEquals(2, compiled.subqueries().size(), "one correlated check per relationship alias");
    assertTrue(compiled.expression().contains(" AND "));
    assertEquals(2, compiled.parameters().values().stream().filter(Boolean.FALSE::equals).count());
    assertTrue(compiled.parameters().containsValue("%alpha%"));
    assertTrue(compiled.parameters().containsValue("%beta%"));
    assertEquals(compiled.parameters().size(), compiled.parameters().keySet().size());
  }
}
