package com.researchspace.dao.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.Field;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.RelationshipTarget;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.ResourceFieldSelections;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.RuntimeFieldBinding;
import com.researchspace.model.collection.RuntimeFieldDefinition;
import com.researchspace.model.collection.RuntimeFieldNamespaces;
import com.researchspace.model.collection.RuntimeFieldSelection;
import com.researchspace.model.collection.RuntimeFieldValueType;
import com.researchspace.model.collection.SplitReferenceBinding;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.field.ExtraTextField;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.search.customfield.RuntimeFieldTextSearch;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IndexedTextNarrowingTest {

  private static final String SELECTOR = "customFields.SF104";
  private static final String EXTRA_SELECTOR = "extraFields.XFt566f6c74616765";

  private static final class RecordingSearch implements RuntimeFieldTextSearch {

    private final Optional<List<Long>> answer;
    private final List<String> asked = new ArrayList<>();
    private final List<Integer> capsAsked = new ArrayList<>();
    private Class<?> askedType;

    private RecordingSearch(Optional<List<Long>> answer) {
      this.answer = answer;
    }

    @Override
    public Optional<List<Long>> matchingIds(
        Class<?> indexedType, String indexField, String text, int maxMatches) {
      capsAsked.add(maxMatches);
      askedType = indexedType;
      asked.add(indexField);
      return answer;
    }

    @Override
    public void reindexAll() {}
  }

  private static RecordingSearch answering(Optional<List<Long>> answer) {
    return new RecordingSearch(answer);
  }

  private static ResolvedRuntimeField customField(RuntimeFieldValueType type) {
    Map<String, Object> match = new LinkedHashMap<>();
    match.put("templateField.id", 104L);
    match.put("deleted", false);
    return new ResolvedRuntimeField(
        new RuntimeFieldDefinition(
            "SF104", SELECTOR, "Hazard class", type, "IT9", "Template", List.of()),
        new RuntimeFieldBinding(InventoryEntityField.class, "instrumentEntity.id", "data", match));
  }

  private static ResolvedRuntimeField extraField() {
    Map<String, Object> match = new LinkedHashMap<>();
    match.put("editInfo.name", "Voltage");
    match.put("deleted", false);
    return new ResolvedRuntimeField(
        new RuntimeFieldDefinition(
            "XFt566f6c74616765",
            EXTRA_SELECTOR,
            "Voltage",
            RuntimeFieldValueType.TEXT,
            null,
            "",
            List.of()),
        new RuntimeFieldBinding(
            ExtraTextField.class, "instrumentEntity.id", "editInfo.description", match));
  }

  private static ResourceRequest request(FilterExpression filter, RuntimeFieldSelection runtime) {
    return new ResourceRequest(
        filter,
        null,
        List.of(new Sort("name", true), new Sort("id", true)),
        new ResourceRequest.Page(1, 20),
        ResourceFieldSelections.root(FieldSelection.all()),
        IncludeTree.empty(),
        runtime);
  }

  private static ResourceRequest request(FilterExpression filter, RuntimeFieldValueType type) {
    return request(
        filter, new RuntimeFieldSelection(Map.of(SELECTOR, customField(type)), Set.of()));
  }

  private static FilterExpression like(String field, String value) {
    return new FilterExpression.Comparison(field, Operator.LIKE, List.of(value), false);
  }

  private static FilterExpression like(String value) {
    return like(SELECTOR, value);
  }

  private static FilterExpression contains(String value) {
    return new FilterExpression.Comparison(SELECTOR, Operator.CONTAINS, List.of(value), false);
  }

  private static ResourceRequest narrow(ResourceRequest request, RuntimeFieldTextSearch search) {
    return IndexedTextNarrowing.apply(request, ApiV2InstrumentResource.DESCRIPTION, search);
  }

  private static ResourceRequest narrowBookings(
      ResourceRequest request, RuntimeFieldTextSearch search) {
    return IndexedTextNarrowing.apply(
        request, ApiV2BookingConfigurationResource.DESCRIPTION, search);
  }

  @Test
  void replacesATextFilterWithTheMatchingIdSet() {
    RecordingSearch search = answering(Optional.of(List.of(7L, 9L)));

    ResourceRequest result = narrow(request(like("BSL"), RuntimeFieldValueType.TEXT), search);

    assertEquals(
        new FilterExpression.Comparison("id", Operator.IN, List.of(7L, 9L), false),
        result.filter());
    assertEquals(List.of("rtFieldValue_customFields_SF104"), search.asked);
    assertEquals(Instrument.class, search.askedType);
  }

  @Test
  void narrowsAnAdHocExtraFieldUnderTheExtraFieldsNamespace() {
    RecordingSearch search = answering(Optional.of(List.of(3L)));
    ResourceRequest request =
        request(
            like(EXTRA_SELECTOR, "high"),
            new RuntimeFieldSelection(Map.of(EXTRA_SELECTOR, extraField()), Set.of()));

    ResourceRequest result = narrow(request, search);

    assertEquals(
        new FilterExpression.Comparison("id", Operator.IN, List.of(3L), false), result.filter());
    assertEquals(
        List.of("rtFieldValue_" + RuntimeFieldNamespaces.EXTRA_FIELDS + "_XFt566f6c74616765"),
        search.asked);
  }

  @Test
  void keepsTheOriginalFilterWhenTheIndexCannotAnswer() {
    ResourceRequest original = request(like("BSL"), RuntimeFieldValueType.TEXT);

    ResourceRequest result = narrow(original, answering(Optional.empty()));

    assertSame(original, result);
  }

  @Test
  void fallsBackToTheDatabaseWhenTheIndexReturnsNothing() {
    ResourceRequest original = request(like("BSL"), RuntimeFieldValueType.TEXT);

    assertSame(original, narrow(original, answering(Optional.of(List.of()))));
  }

  @Test
  void leavesOperatorsOtherThanLikeAlone() {
    ResourceRequest original =
        request(
            new FilterExpression.Comparison(SELECTOR, Operator.EQUAL, List.of("BSL-2"), false),
            RuntimeFieldValueType.TEXT);

    assertSame(original, narrow(original, answering(Optional.of(List.of(7L)))));
  }

  @Test
  void leavesContainsOnTheDatabaseSoItStaysAnExactSubstringMatch() {
    ResourceRequest original = request(contains("BSL"), RuntimeFieldValueType.TEXT);

    assertSame(original, narrow(original, answering(Optional.of(List.of(7L)))));
  }

  @Test
  void leavesNonTextDefinitionsAlone() {
    ResourceRequest original = request(contains("BSL"), RuntimeFieldValueType.CHOICE);

    assertSame(original, narrow(original, answering(Optional.of(List.of(7L)))));
  }

  @Test
  void keepsSiblingFiltersWhenOneIsRewritten() {
    FilterExpression both =
        new FilterExpression.And(
            List.of(
                new FilterExpression.Comparison("name", Operator.CONTAINS, List.of("scope"), false),
                like("BSL")));

    ResourceRequest result =
        narrow(request(both, RuntimeFieldValueType.TEXT), answering(Optional.of(List.of(7L))));

    FilterExpression.And and = (FilterExpression.And) result.filter();
    assertEquals(2, and.children().size());
    assertTrue(
        and.children()
            .contains(new FilterExpression.Comparison("id", Operator.IN, List.of(7L), false)));
    assertTrue(
        and.children()
            .contains(
                new FilterExpression.Comparison(
                    "name", Operator.CONTAINS, List.of("scope"), false)));
  }

  @Test
  void rewritesOneBranchOfAnOrAndLeavesTheOther() {
    FilterExpression either =
        new FilterExpression.Or(
            List.of(
                new FilterExpression.Comparison("name", Operator.CONTAINS, List.of("scope"), false),
                like("BSL")));

    ResourceRequest result =
        narrow(request(either, RuntimeFieldValueType.TEXT), answering(Optional.of(List.of(7L))));

    FilterExpression.Or or = (FilterExpression.Or) result.filter();
    assertEquals(
        List.of(
            new FilterExpression.Comparison("name", Operator.CONTAINS, List.of("scope"), false),
            new FilterExpression.Comparison("id", Operator.IN, List.of(7L), false)),
        or.children());
  }

  @Test
  void narrowsARelationshipScalarByTargetIdAndKeepsTheOriginalPredicate() {
    RecordingSearch search = answering(Optional.of(List.of(11L, 12L)));
    FilterExpression original = like("target.name", "confocal");

    ResourceRequest result =
        narrowBookings(request(original, RuntimeFieldSelection.empty()), search);

    assertEquals(
        new FilterExpression.And(
            List.of(
                new FilterExpression.Comparison(
                    "target.value", Operator.IN, List.of(11L, 12L), false),
                original)),
        result.filter());
    assertEquals(List.of("name"), search.asked);
    assertEquals(Instrument.class, search.askedType);
  }

  @Test
  void narrowsATargetRuntimeFieldByTargetId() {
    RecordingSearch search = answering(Optional.of(List.of(11L)));
    String hopped = "target." + SELECTOR;
    FilterExpression original = like(hopped, "BSL");
    RuntimeFieldSelection runtime =
        new RuntimeFieldSelection(
            Map.of(hopped, customField(RuntimeFieldValueType.TEXT)),
            Set.of(),
            Map.of(hopped, "target"));

    ResourceRequest result = narrowBookings(request(original, runtime), search);

    assertEquals(
        new FilterExpression.And(
            List.of(
                new FilterExpression.Comparison("target.value", Operator.IN, List.of(11L), false),
                original)),
        result.filter());
    assertEquals(List.of("rtFieldValue_customFields_SF104"), search.asked);
    assertEquals(Instrument.class, search.askedType);
  }

  @Test
  void leavesAPolymorphicRelationshipAlone() {
    RecordingSearch search = answering(Optional.of(List.of(11L)));
    ResourceRequest original =
        request(like("target.name", "confocal"), RuntimeFieldSelection.empty());

    ResourceRequest result =
        IndexedTextNarrowing.apply(original, twoDestinationDescription(), search);

    assertSame(original, result);
    assertEquals(List.of(), search.asked);
  }

  @Test
  void leavesANameThatIsNotARelationshipAlone() {
    ResourceRequest original =
        request(like("owner.name", "confocal"), RuntimeFieldSelection.empty());

    assertSame(original, narrowBookings(original, answering(Optional.of(List.of(11L)))));
  }

  @Test
  void leavesASecondHopAlone() {
    ResourceRequest original =
        request(like("target.owner.name", "confocal"), RuntimeFieldSelection.empty());

    assertSame(original, narrowBookings(original, answering(Optional.of(List.of(11L)))));
  }

  private static CollectionDescription<Related> twoDestinationDescription() {
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
                        "instruments", TargetKind.INSTRUMENT, "IN", Instrument.class),
                    new RelationshipTarget<>("samples", TargetKind.SAMPLE, "SA", Related.class)),
                new SplitReferenceBinding<>(Related::target, "targetType", "targetId"))),
        "id",
        List.of(new Sort("id", true)));
  }

  private record Related(Long id, TargetKind targetType, Long targetId) {
    ResourceReference<TargetKind, Long> target() {
      return new ResourceReference<>(targetType, targetId);
    }
  }

  private enum TargetKind {
    INSTRUMENT,
    SAMPLE
  }

  @Test
  void leavesARelationshipScalarAloneWhenTheIndexAnswersWithTooManyIds() {
    List<Long> tooMany = java.util.stream.LongStream.rangeClosed(1, 2001).boxed().toList();
    RecordingSearch search = answering(Optional.of(tooMany));
    ResourceRequest original =
        request(like("target.name", "microscope"), RuntimeFieldSelection.empty());

    assertSame(original, narrowBookings(original, search));
    assertEquals(List.of("name"), search.asked);
    assertEquals(List.of(2_000), search.capsAsked);
  }

  @Test
  void keepsNarrowingATargetRuntimeFieldWithALargeIdSet() {
    List<Long> many = java.util.stream.LongStream.rangeClosed(1, 2001).boxed().toList();
    String hopped = "target." + SELECTOR;
    FilterExpression original = like(hopped, "BSL");
    RuntimeFieldSelection runtime =
        new RuntimeFieldSelection(
            Map.of(hopped, customField(RuntimeFieldValueType.TEXT)),
            Set.of(),
            Map.of(hopped, "target"));

    ResourceRequest result =
        narrowBookings(request(original, runtime), answering(Optional.of(many)));

    FilterExpression.And and = (FilterExpression.And) result.filter();
    assertEquals(2, and.children().size());
  }
}
