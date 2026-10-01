package com.researchspace.dao.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.dao.query.RsqlCollectionQuery.Predicate;
import com.researchspace.dao.query.RsqlCollectionQuery.Subquery;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.maintenance.model.ApiV2MaintenanceResource;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.booking.ApiV2BookingInstrumentResource;
import com.researchspace.model.booking.ApiV2TimeSlotBookingResource;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.CollectionQueryLimits;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.QueryConstraint;
import com.researchspace.model.collection.Relationship;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.RelationshipTarget;
import com.researchspace.model.collection.ResourceMember;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRoleMembershipConstraint;
import com.researchspace.model.collection.RsqlFilterParser;
import com.researchspace.model.collection.RuntimeFieldSelection;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.collection.SplitReferenceBinding;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RsqlCollectionQueryTest {

  private final RsqlCollectionQuery translator =
      new RsqlCollectionQuery(ApiV2MaintenanceResource.DESCRIPTION, "item");
  private final RsqlFilterParser parser =
      new RsqlFilterParser(ApiV2MaintenanceResource.DESCRIPTION);

  @Test
  void translatesNestedPredicatesWithBoundTypedParameters() {
    Predicate result = translate("id=in=(1,2);(startDate>2026-07-24T00:00:00Z,message==*upgrade*)");

    assertEquals(
        "(item.id IN :rsql0 AND (item.startDate > :rsql1 OR "
            + "LOWER(item.message) LIKE LOWER(:rsql2) ESCAPE '!'))",
        result.expression());
    assertEquals(
        Map.of(
            "rsql0",
            List.of(1L, 2L),
            "rsql1",
            Date.from(Instant.parse("2026-07-24T00:00:00Z")),
            "rsql2",
            "%upgrade%"),
        result.parameters());
  }

  @Test
  void translatesPayloadStringOperatorsAndEscapesLikeWildcards() {
    Predicate result =
        translate(
            "message=contains='50%_done!';message=like='database upgrade';message=exists=true");

    assertEquals(
        "(LOWER(item.message) LIKE LOWER(:rsql0) ESCAPE '!' AND "
            + "(LOWER(item.message) LIKE LOWER(:rsql1) ESCAPE '!' AND "
            + "LOWER(item.message) LIKE LOWER(:rsql2) ESCAPE '!') AND "
            + "item.message IS NOT NULL)",
        result.expression());
    assertEquals(
        Map.of(
            "rsql0", "%50!%!_done!!%",
            "rsql1", "%database%",
            "rsql2", "%upgrade%"),
        result.parameters());
  }

  @Test
  void parsesRepresentativeRsqlBuilderOutputWithoutLosingNestedPrecedence() {
    FilterExpression.And root =
        assertInstanceOf(
            FilterExpression.And.class,
            parser.parse("(message==\"a(\",id==1);message=contains=\"Ada Lovelace\""));
    FilterExpression.Or nested =
        assertInstanceOf(FilterExpression.Or.class, root.children().get(0));
    FilterExpression.Comparison escapedValue =
        assertInstanceOf(FilterExpression.Comparison.class, nested.children().get(0));
    FilterExpression.Comparison contains =
        assertInstanceOf(FilterExpression.Comparison.class, root.children().get(1));

    assertEquals(List.of("a("), escapedValue.values());
    assertEquals(Operator.CONTAINS, contains.operator());
    assertEquals(List.of("Ada Lovelace"), contains.values());
  }

  @Test
  void rejectsUnknownFieldsOperatorsAndInvalidValues() {
    assertThrows(CollectionQueryException.class, () -> translate("unknown==1"));
    assertThrows(CollectionQueryException.class, () -> translate("message>abc"));
    assertThrows(CollectionQueryException.class, () -> translate("id==abc"));
    assertThrows(CollectionQueryException.class, () -> translate("id=exists=true"));
    assertThrows(CollectionQueryException.class, () -> translate("message=exists=perhaps"));
    assertThrows(CollectionQueryException.class, () -> translate("message=contains=(one,two)"));
    assertThrows(CollectionQueryException.class, () -> translate("message=exists=(true,false)"));
    assertThrows(
        CollectionQueryException.class,
        () ->
            translator.translate(
                new FilterExpression.Comparison(
                    "unregisteredProperty", Operator.EQUAL, List.of("value"), false)));
  }

  @Test
  void acceptsTheUnquotedIsoInstantFilterTheMaintenanceBannerSends() {
    // The banner sends where=endDate>{new Date().toISOString()} to skip expired windows, so the
    // millisecond-precision ISO form must parse unquoted.
    Predicate result = translate("endDate>2026-07-28T16:20:49.441Z");

    assertEquals("item.endDate > :rsql0", result.expression());
    assertEquals(
        Map.of("rsql0", Date.from(Instant.parse("2026-07-28T16:20:49.441Z"))), result.parameters());
  }

  @Test
  void rejectsBlankContainsLikeLikeAndOversizeWildcardEquality() {
    assertThrows(CollectionQueryException.class, () -> translate("message=contains=' '"));
    assertThrows(CollectionQueryException.class, () -> translate("message=like=' '"));

    String tooLong = "*" + "a".repeat(300) + "*";
    assertThrows(CollectionQueryException.class, () -> translate("message==" + tooLong));
  }

  @Test
  void enforcesComparisonArgumentAndNestingLimits() {
    String comparisons =
        IntStream.rangeClosed(1, 51)
            .mapToObj(value -> "id==" + value)
            .collect(Collectors.joining(";"));
    String arguments =
        "id=in=("
            + IntStream.rangeClosed(1, CollectionQueryLimits.MAX_ARGUMENTS + 1)
                .mapToObj(Integer::toString)
                .collect(Collectors.joining(","))
            + ")";
    String nested = "id==0";
    for (int depth = 1; depth <= 11; depth++) {
      nested = "id==" + depth + ";(" + nested + ")";
    }
    String deeplyNested = nested;
    String redundantParentheses = "(".repeat(11) + "id==1" + ")".repeat(11);

    assertThrows(CollectionQueryException.class, () -> translate(comparisons));
    assertThrows(CollectionQueryException.class, () -> translate(arguments));
    assertThrows(CollectionQueryException.class, () -> translate(deeplyNested));
    assertThrows(CollectionQueryException.class, () -> translate(redundantParentheses));
  }

  @Test
  void limitsTheTotalNumberOfGeneratedLikePredicates() {
    String fiftyWords =
        IntStream.range(0, 50).mapToObj(ignored -> "a").collect(Collectors.joining(" "));
    String fiftyOneWords = fiftyWords + " a";

    Predicate accepted = translate("message=like='" + fiftyWords + "'");

    assertEquals(50, accepted.parameters().size());
    CollectionQueryException rejected =
        assertThrows(
            CollectionQueryException.class,
            () -> translate("message=like='" + fiftyOneWords + "'"));
    assertEquals(CollectionQueryException.Reason.COMPLEXITY, rejected.getReason());
  }

  @Test
  void doesNotChargeTrustedAccessConstraintsToTheCallerLikeBudget() {
    FilterExpression constraint =
        new FilterExpression.Or(
            IntStream.rangeClosed(1, 51)
                .<FilterExpression>mapToObj(
                    value ->
                        new FilterExpression.Comparison(
                            "message", Operator.CONTAINS, List.of("group-" + value), false))
                .toList());

    Predicate translated = translator.translateTrusted(constraint);

    assertEquals(51, translated.parameters().size());
  }

  @Test
  void compilesTrustedResourceRoleMembershipAsOneCorrelatedExists() {
    RsqlCollectionQuery bookingTranslator =
        new RsqlCollectionQuery(ApiV2BookingConfigurationResource.DESCRIPTION, "configuration");
    ResourceRoleMembershipConstraint constraint =
        new ResourceRoleMembershipConstraint(
            "resourceAccess.id", 12L, Set.of(41L, 42L), Set.of("OWNER", "VIEWER"), true);

    Predicate translated = bookingTranslator.translateTrusted(constraint);

    assertEquals(1, translated.subqueries().size());
    assertTrue(translated.expression().startsWith("EXISTS "));
    assertTrue(where(translated).contains("configuration.resourceAccess.id"));
    assertTrue(translated.parameters().containsValue(12L));
    assertTrue(translated.parameters().containsValue(Set.of(41L, 42L)));
    assertTrue(translated.parameters().containsValue(Set.of("OWNER", "VIEWER")));
  }

  @Test
  void compilesTrustedMembershipOrCallerOwnedRowAsOnePredicate() {
    RsqlCollectionQuery bookingTranslator =
        new RsqlCollectionQuery(ApiV2TimeSlotBookingResource.DESCRIPTION, "booking");
    QueryConstraint constraint =
        new QueryConstraint.Or(
            List.of(
                new ResourceRoleMembershipConstraint(
                    "bookingConfiguration.resourceAccess.id",
                    12L,
                    Set.of(41L),
                    Set.of("OWNER", "VIEWER"),
                    true),
                new FilterExpression.Comparison(
                    "requesterId", Operator.EQUAL, List.of(12L), false)));

    Predicate translated = bookingTranslator.translateTrusted(constraint);

    assertEquals(1, translated.subqueries().size());
    assertTrue(translated.expression().contains(" OR "));
    assertTrue(translated.expression().contains("booking.requester.id"));
    assertTrue(where(translated).contains("booking.bookingConfiguration.resourceAccess.id"));
  }

  @Test
  void translatesSplitRelationshipSelectorsThroughReadableTargets() {
    CollectionDescription<Related> description = relationshipDescription();
    RsqlFilterParser relationshipParser = new RsqlFilterParser(description);
    RsqlCollectionQuery relationshipTranslator = new RsqlCollectionQuery(description, "item");
    RelationshipReadAccess targets = unrestrictedTargets(description);

    Predicate pairs =
        relationshipTranslator.translate(
            where(relationshipParser, "target=in=(IN1,SA2);target.value==3"), targets);
    assertEquals(4, pairs.subqueries().size());
    assertTrue(pairs.expression().contains("EXISTS"));
    assertTrue(pairs.expression().contains("item.targetId = :"));
    assertTrue(pairs.parameters().containsValue(3L));

    Predicate kind =
        relationshipTranslator.translate(
            where(relationshipParser, "target.relationTo==instruments"), targets);
    assertEquals(2, kind.subqueries().size());
    assertTrue(kind.expression().contains("item.targetType = :"));
    assertTrue(kind.parameters().containsValue(TargetKind.INSTRUMENT));

    Predicate excluded =
        relationshipTranslator.translate(
            where(relationshipParser, "target=out=(IN1,SA2)"), targets);
    assertEquals(2, excluded.subqueries().size());
    assertTrue(excluded.expression().contains(" AND NOT "));
    assertThrows(
        CollectionQueryException.class, () -> where(relationshipParser, "target.globalId==IN1"));
    assertThrows(CollectionQueryException.class, () -> where(relationshipParser, "target==XX1"));
  }

  @Test
  void compilesReadableRelationshipWithoutRequiringACallerFilter() {
    CollectionDescription<Related> description = relationshipDescription();
    RsqlCollectionQuery relationshipTranslator = new RsqlCollectionQuery(description, "item");
    RelationshipReadAccess targets = unrestrictedTargets(description);

    Predicate allowed = relationshipTranslator.compileReadableRelationship("target", targets);
    Predicate denied =
        relationshipTranslator.compileReadableRelationship("target", RelationshipReadAccess.none());

    assertEquals(2, allowed.subqueries().size());
    assertTrue(allowed.expression().contains(" OR "));
    assertTrue(allowed.parameters().containsValue(TargetKind.INSTRUMENT));
    assertTrue(allowed.parameters().containsValue(TargetKind.SAMPLE));
    assertEquals("(1 = 0)", denied.expression());
    assertThrows(
        CollectionQueryException.class,
        () -> relationshipTranslator.compileReadableRelationship("unknown", targets));
  }

  @Test
  void compilesTargetAccessConstraintsInsideEachRelationshipSubquery() {
    CollectionDescription<Related> description = relationshipDescription();
    AccessFunction excludesNine =
        AccessFunction.documented(
            "Test target access.",
            Set.of(),
            ignored ->
                AccessResult.allowedWhere(
                    new FilterExpression.Comparison("id", Operator.NOT_EQUAL, List.of(9L), false)));
    ResourceRegistry registry =
        new ResourceRegistry(
            List.of(
                description,
                relationshipTargetDescription(
                    "instruments", RelatedTarget.class, RelatedTarget::id, excludesNine),
                relationshipTargetDescription(
                    "samples", RelatedSample.class, RelatedSample::id, excludesNine)));
    RelationshipReadAccess targets = RelationshipReadAccess.forActor(registry, new User("reader"));

    Predicate result =
        new RsqlCollectionQuery(description, "item").compileReadableRelationship("target", targets);

    assertEquals(2, result.subqueries().size());
    assertEquals(
        2,
        result.parameters().values().stream()
            .filter(value -> Long.valueOf(9L).equals(value))
            .count());
    assertTrue(
        result.subqueries().values().stream()
            .allMatch(subquery -> subquery.whereExpression().contains(" <> ")));
  }

  @Test
  void compilesAFixedTargetIdentityByItsStoredIdAlone() {
    CollectionDescription<Assigned> assigned =
        new CollectionDescription<>(
            "assigned",
            Assigned.class,
            List.<ResourceMember<Assigned>>of(
                Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Assigned::id),
                Relationship.polymorphicToOne(
                        "instrument",
                        CollectionFieldTypes.longNumber(),
                        List.of(
                            new RelationshipTarget<>(
                                "instruments", "instruments", "IN", RelatedTarget.class)),
                        SplitReferenceBinding.monomorphic(Assigned::instrument, "instrumentId"))
                    .writeOnlyOn()),
            "id",
            List.of(new Sort("id", true)));
    RelationshipReadAccess targets = unrestrictedTargets(assigned);

    Predicate result =
        new RsqlCollectionQuery(assigned, "item")
            .translate(
                where(new RsqlFilterParser(assigned), "instrument=in=(IN42,IN43);instrument!=IN44"),
                targets);

    assertTrue(result.expression().contains("(item.instrumentId IN :"), result.expression());
    assertTrue(result.expression().contains("NOT (item.instrumentId IN :"), result.expression());
    assertFalse(result.expression().contains(".null"), result.expression());
    assertTrue(result.parameters().containsValue(List.of(42L, 43L)));
    assertTrue(result.parameters().containsValue(List.of(44L)));
  }

  @Test
  void enforcesOneLikeBudgetAcrossDirectAndNestedRelationshipPredicates() {
    LikeBudgetFixture fixture = likeBudgetFixture();
    RsqlCollectionQuery query = new RsqlCollectionQuery(fixture.source(), "item");

    Predicate fortyNine =
        query.translate(
            new FilterExpression.And(
                List.of(likeComparison("name", 29), likeComparison("target.name", 20))),
            fixture.targets());
    Predicate fifty =
        query.translate(
            new FilterExpression.And(
                List.of(likeComparison("name", 30), likeComparison("target.name", 20))),
            fixture.targets());

    assertEquals(49, likeParameterCount(fortyNine));
    assertEquals(50, likeParameterCount(fifty));
    CollectionQueryException overBudget =
        assertThrows(
            CollectionQueryException.class,
            () ->
                query.translate(
                    new FilterExpression.And(
                        List.of(likeComparison("name", 31), likeComparison("target.name", 20))),
                    fixture.targets()));
    assertEquals(CollectionQueryException.Reason.COMPLEXITY, overBudget.getReason());

    CollectionQueryException nestedOverBudget =
        assertThrows(
            CollectionQueryException.class,
            () ->
                query.translate(
                    new FilterExpression.And(
                        List.of(
                            likeComparison("target.name", 30), likeComparison("target.name", 30))),
                    fixture.targets()));
    assertEquals(CollectionQueryException.Reason.COMPLEXITY, nestedOverBudget.getReason());
  }

  @Test
  void trustedRelationshipConstraintKeepsItsNestedLikesOutsideTheCallerBudget() {
    LikeBudgetFixture fixture = likeBudgetFixture();
    RsqlCollectionQuery query = new RsqlCollectionQuery(fixture.source(), "item");

    Predicate trusted =
        query.translateTrusted(likeComparison("target.name", 51), fixture.targets());

    assertEquals(51, likeParameterCount(trusted));
  }

  @Test
  void composesPresentPredicatesWithoutChangingBooleanGrouping() {
    Predicate either =
        new Predicate("item.id = :one OR item.id = :two", Map.of("one", 1L, "two", 2L));
    Predicate after = new Predicate("item.id > :zero", Map.of("zero", 0L));

    assertNull(Predicate.and(null, null));
    assertNull(Predicate.or());
    assertSame(either, Predicate.and(null, either));
    assertEquals(
        "(item.id = :one OR item.id = :two) AND (item.id > :zero)",
        Predicate.and(either, null, after).expression());
    assertEquals(
        "(item.id = :one OR item.id = :two) OR (item.id > :zero)",
        Predicate.or(either, after).expression());
  }

  @Test
  void rejectsConflictingBindingsButAllowsEqualMappings() {
    Subquery original = new Subquery(RelatedTarget.class, "target", "target.id = item.id");
    Predicate first = new Predicate("EXISTS check", Map.of("id", 1L), Map.of("check", original), 2);
    Predicate same = new Predicate("EXISTS check", Map.of("id", 1L), Map.of("check", original), 3);
    Predicate differentParameter = new Predicate("item.id = :id", Map.of("id", 2L));
    Predicate differentSubquery =
        new Predicate(
            "EXISTS check",
            Map.of(),
            Map.of("check", new Subquery(RelatedSample.class, "target", "target.id = item.id")));

    Predicate reused = Predicate.and(first, same);
    assertEquals(first.parameters(), reused.parameters());
    assertEquals(first.subqueries(), reused.subqueries());
    assertEquals(5, reused.callerLikePredicateCount());
    assertThrows(IllegalArgumentException.class, () -> Predicate.and(first, differentParameter));
    assertThrows(IllegalArgumentException.class, () -> Predicate.or(first, differentSubquery));

    RsqlCompilationState state =
        new RsqlCompilationState(
            new RsqlCompilationState.Names("test"),
            RelationshipReadAccess.none(),
            RuntimeFieldSelection.empty(),
            true);
    state.merge(first);
    state.merge(same);
    state.addSubquery(original);
    state.addSubquery(original);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            state.addSubquery(new Subquery(RelatedSample.class, "target", "target.id = item.id")));
    assertThrows(IllegalArgumentException.class, () -> state.merge(differentParameter));
    assertThrows(IllegalArgumentException.class, () -> state.merge(differentSubquery));
  }

  @Test
  void sharesNamesWithinAnOperationAndRestartsThemForIndependentCompilations() {
    LikeBudgetFixture fixture = likeBudgetFixture();
    RsqlCollectionQuery query = new RsqlCollectionQuery(fixture.source(), "item");
    RsqlCollectionQuery.Compilation compilation = query.newCompilation();
    FilterExpression filter = likeComparison("target.name", 1);

    Predicate caller = compilation.translate(filter, fixture.targets());
    Predicate access = compilation.translateTrusted(filter, fixture.targets());
    Predicate combined = Predicate.and(caller, access);

    assertEquals(2, combined.parameters().size());
    assertEquals(2, combined.subqueries().size());
    assertEquals(1, combined.callerLikePredicateCount());
    assertEquals(
        query.translate(filter, fixture.targets()), query.translate(filter, fixture.targets()));
  }

  @Test
  void keepsNestedAccessSubqueriesInsideEachCorrelatedTargetAndOutsideTheCallerBudget() {
    LikeBudgetFixture fixture = likeBudgetFixture();
    CollectionDescription<BudgetTarget> restrictedTarget =
        new CollectionDescription<>(
            "budgetTargets",
            BudgetTarget.class,
            List.of(
                Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), BudgetTarget::id),
                Field.readOnly("name", "name", CollectionFieldTypes.text(), BudgetTarget::name)),
            "id",
            List.of(new Sort("id", true)),
            AccessPolicy.readOnly(
                AccessFunction.documented(
                    "Nested target read rule.",
                    Set.of(),
                    ignored ->
                        AccessResult.allowedWhere(
                            new QueryConstraint.And(
                                List.of(
                                    new ResourceRoleMembershipConstraint(
                                        "id", 12L, Set.of(), Set.of("VIEWER"), false),
                                    likeComparison("name", 51)))))));
    RelationshipReadAccess targets =
        RelationshipReadAccess.forActor(
            new ResourceRegistry(List.of(fixture.source(), restrictedTarget)), null);

    Predicate result =
        new RsqlCollectionQuery(fixture.source(), "item")
            .translate(
                new FilterExpression.And(
                    List.of(likeComparison("name", 30), likeComparison("target.name", 20))),
                targets);

    assertEquals(50, result.callerLikePredicateCount());
    assertEquals(101, likeParameterCount(result));
    assertEquals(1, result.subqueries().size());
    Subquery target = result.subqueries().values().iterator().next();
    assertEquals(1, target.subqueries().size());
    Subquery membership = target.subqueries().values().iterator().next();
    assertFalse(target.alias().equals(membership.alias()));
    assertTrue(membership.whereExpression().contains(" = " + target.alias() + ".id"));
    assertTrue(
        target
            .whereExpression()
            .contains("EXISTS " + target.subqueries().keySet().iterator().next()));
  }

  private static FilterExpression.Comparison likeComparison(String field, int wordCount) {
    String words =
        IntStream.range(0, wordCount)
            .mapToObj(index -> "term" + index)
            .collect(Collectors.joining(" "));
    return new FilterExpression.Comparison(field, Operator.LIKE, List.of(words), false);
  }

  private static long likeParameterCount(Predicate predicate) {
    return predicate.parameters().values().stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .filter(value -> value.startsWith("%") && value.endsWith("%"))
        .count();
  }

  private static LikeBudgetFixture likeBudgetFixture() {
    CollectionDescription<BudgetSource> source =
        new CollectionDescription<>(
            "budgetSources",
            BudgetSource.class,
            List.<ResourceMember<BudgetSource>>of(
                Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), BudgetSource::id),
                Field.readOnly("name", "name", CollectionFieldTypes.text(), BudgetSource::name),
                Relationship.referenceToOne(
                    "target",
                    "budgetTargets",
                    CollectionFieldTypes.longNumber(),
                    BudgetTarget.class,
                    BudgetSource::target,
                    BudgetTarget::id,
                    "targetId")),
            "id",
            List.of(new Sort("id", true)));
    CollectionDescription<BudgetTarget> target =
        new CollectionDescription<>(
            "budgetTargets",
            BudgetTarget.class,
            List.<ResourceMember<BudgetTarget>>of(
                Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), BudgetTarget::id),
                Field.readOnly("name", "name", CollectionFieldTypes.text(), BudgetTarget::name)),
            "id",
            List.of(new Sort("id", true)));
    ResourceRegistry registry = new ResourceRegistry(List.of(source, target));
    return new LikeBudgetFixture(source, RelationshipReadAccess.unrestricted(registry));
  }

  @Test
  void translatesAutomaticAuditFieldAndUserRelationshipFilters() {
    CollectionDescription<?> description = ApiV2BookingConfigurationResource.DESCRIPTION;
    RsqlFilterParser auditParser = new RsqlFilterParser(description);
    RsqlCollectionQuery auditTranslator = new RsqlCollectionQuery(description, "item");
    RelationshipReadAccess targets =
        RelationshipReadAccess.unrestricted(
            new ResourceRegistry(
                List.of(
                    description,
                    ApiV2UserResource.DESCRIPTION,
                    ApiV2BookingInstrumentResource.DESCRIPTION,
                    ApiV2InstrumentResource.DESCRIPTION,
                    com.researchspace.model.booking.ApiV2BookingLocationResource.DESCRIPTION)));

    Predicate result =
        auditTranslator.translate(
            auditParser.parse(
                "createdAt>=2026-08-01T00:00:00Z;updatedAt<2026-08-03T00:00:00Z;"
                    + "createdBy.value==21;updatedBy.value==22"),
            targets);

    assertEquals(2, result.subqueries().size());
    assertTrue(result.expression().contains("item.createdBy.id = :"));
    assertTrue(result.expression().contains("item.updatedBy.id = :"));
    assertTrue(result.parameters().containsValue(Date.from(Instant.parse("2026-08-01T00:00:00Z"))));
    assertTrue(result.parameters().containsValue(Date.from(Instant.parse("2026-08-03T00:00:00Z"))));
    assertTrue(result.parameters().containsValue(21L));
    assertTrue(result.parameters().containsValue(22L));
  }

  private static CollectionDescription<Related> relationshipDescription() {
    return new CollectionDescription<>(
        "related",
        Related.class,
        List.<ResourceMember<Related>>of(
            Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Related::id),
            Relationship.polymorphicToOne(
                "target",
                CollectionFieldTypes.longNumber(),
                List.of(
                    new RelationshipTarget<>(
                        "instruments", TargetKind.INSTRUMENT, "IN", RelatedTarget.class),
                    new RelationshipTarget<>(
                        "samples", TargetKind.SAMPLE, "SA", RelatedSample.class)),
                new SplitReferenceBinding<>(Related::target, "targetType", "targetId"))),
        "id",
        List.of(new Sort("id", true)));
  }

  private static <T> CollectionDescription<T> relationshipTargetDescription(
      String name, Class<T> entityType, Function<T, Long> idAccessor) {
    return relationshipTargetDescription(name, entityType, idAccessor, AccessFunction.anyone());
  }

  private static <T> CollectionDescription<T> relationshipTargetDescription(
      String name, Class<T> entityType, Function<T, Long> idAccessor, AccessFunction readAccess) {
    return new CollectionDescription<>(
        name,
        entityType,
        List.of(Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), idAccessor)),
        "id",
        List.of(new Sort("id", true)),
        AccessPolicy.readOnly(readAccess));
  }

  private static RelationshipReadAccess unrestrictedTargets(CollectionDescription<?> description) {
    return RelationshipReadAccess.unrestricted(
        new ResourceRegistry(
            List.of(
                description,
                relationshipTargetDescription(
                    "instruments", RelatedTarget.class, RelatedTarget::id),
                relationshipTargetDescription("samples", RelatedSample.class, RelatedSample::id))));
  }

  private static FilterExpression where(RsqlFilterParser parser, String expression) {
    return parser.parse(expression);
  }

  private static String where(Predicate predicate) {
    return predicate.subqueries().values().iterator().next().whereExpression();
  }

  private Predicate translate(String rsql) {
    return translator.translate(parser.parse(rsql));
  }

  private record LikeBudgetFixture(
      CollectionDescription<BudgetSource> source, RelationshipReadAccess targets) {}

  private record BudgetSource(Long id, String name, Long targetId) {
    BudgetTarget target() {
      return new BudgetTarget(targetId, "target");
    }
  }

  private record BudgetTarget(Long id, String name) {}

  private record Related(Long id, TargetKind targetType, Long targetId) {
    ResourceReference<TargetKind, Long> target() {
      return new ResourceReference<>(targetType, targetId);
    }
  }

  private record RelatedTarget(Long id) {}

  /** A single-target relationship, which stores only the target ID. */
  private record Assigned(Long id, Long instrumentId) {
    ResourceReference<String, Long> instrument() {
      return new ResourceReference<>("instruments", instrumentId);
    }
  }

  private record RelatedSample(Long id) {}

  private enum TargetKind {
    INSTRUMENT,
    SAMPLE
  }
}
