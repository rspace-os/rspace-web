package com.researchspace.dao.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.dao.query.RsqlCollectionQuery.Predicate;
import com.researchspace.maintenance.model.ApiV2MaintenanceResource;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.Field;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.RelationshipTarget;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.RsqlFilterParser;
import com.researchspace.model.collection.SplitReferenceBinding;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
            + IntStream.rangeClosed(1, 101)
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
  void translatesSplitRelationshipSelectorsAndKeepsGlobalIdPairsTogether() {
    CollectionDescription<Related> description = relationshipDescription();
    RsqlFilterParser relationshipParser = new RsqlFilterParser(description);
    RsqlCollectionQuery relationshipTranslator = new RsqlCollectionQuery(description, "item");

    Predicate pairs =
        relationshipTranslator.translate(
            relationshipParser.parse("target=in=(IN1,SA2);target.value==3"));
    assertEquals(
        "(((item.targetType = :rsql0 AND item.targetId = :rsql1) OR "
            + "(item.targetType = :rsql2 AND item.targetId = :rsql3)) AND "
            + "item.targetId = :rsql4)",
        pairs.expression());
    assertEquals(
        Map.of(
            "rsql0", TargetKind.INSTRUMENT,
            "rsql1", 1L,
            "rsql2", TargetKind.SAMPLE,
            "rsql3", 2L,
            "rsql4", 3L),
        pairs.parameters());

    Predicate kind =
        relationshipTranslator.translate(
            relationshipParser.parse("target.relationTo==instruments"));
    assertEquals("item.targetType = :rsql0", kind.expression());
    assertEquals(Map.of("rsql0", TargetKind.INSTRUMENT), kind.parameters());

    Predicate excluded =
        relationshipTranslator.translate(relationshipParser.parse("target=out=(IN1,SA2)"));
    assertEquals(
        "NOT ((item.targetType = :rsql0 AND item.targetId = :rsql1) OR "
            + "(item.targetType = :rsql2 AND item.targetId = :rsql3))",
        excluded.expression());
    assertThrows(
        CollectionQueryException.class, () -> relationshipParser.parse("target.globalId==IN1"));
    assertThrows(CollectionQueryException.class, () -> relationshipParser.parse("target==XX1"));
  }

  @Test
  void translatesAutomaticAuditFieldAndUserRelationshipFilters() {
    CollectionDescription<?> description = ApiV2BookingConfigurationResource.DESCRIPTION;
    RsqlFilterParser auditParser = new RsqlFilterParser(description);
    RsqlCollectionQuery auditTranslator = new RsqlCollectionQuery(description, "item");

    Predicate result =
        auditTranslator.translate(
            auditParser.parse(
                "createdAt>=2026-08-01T00:00:00Z;updatedAt<2026-08-03T00:00:00Z;"
                    + "createdBy.value==21;updatedBy.value==22"));

    assertEquals(
        "(item.createdAt >= :rsql0 AND item.updatedAt < :rsql1 AND "
            + "item.createdBy.id = :rsql2 AND item.updatedBy.id = :rsql3)",
        result.expression());
    assertEquals(
        Map.of(
            "rsql0",
            Date.from(Instant.parse("2026-08-01T00:00:00Z")),
            "rsql1",
            Date.from(Instant.parse("2026-08-03T00:00:00Z")),
            "rsql2",
            21L,
            "rsql3",
            22L),
        result.parameters());
  }

  private static CollectionDescription<Related> relationshipDescription() {
    return new CollectionDescription<>(
        "related",
        Related.class,
        List.of(Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Related::id)),
        List.of(
            CollectionDescription.Relationship.polymorphicToOne(
                "target",
                CollectionFieldTypes.longNumber(),
                List.of(
                    new RelationshipTarget<>(
                        "instruments", TargetKind.INSTRUMENT, "IN", RelatedTarget.class),
                    new RelationshipTarget<>(
                        "samples", TargetKind.SAMPLE, "SA", RelatedTarget.class)),
                new SplitReferenceBinding<>(Related::target, "targetType", "targetId"))),
        "id",
        List.of(new Sort("id", true)));
  }

  private Predicate translate(String rsql) {
    return translator.translate(parser.parse(rsql));
  }

  private record Related(Long id, TargetKind targetType, Long targetId) {
    ResourceReference<TargetKind, Long> target() {
      return new ResourceReference<>(targetType, targetId);
    }
  }

  private record RelatedTarget(Long id) {}

  private enum TargetKind {
    INSTRUMENT,
    SAMPLE
  }
}
