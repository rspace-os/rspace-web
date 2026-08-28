package com.researchspace.api.v2.query;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.api.v2.controller.ApiV2BadRequestException;
import com.researchspace.api.v2.model.ApiV2FieldsetQuery;
import com.researchspace.api.v2.model.ApiV2ListResult;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.maintenance.model.ApiV2MaintenanceResource;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.CollectionQueryLimits;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRenderer;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.RsqlFilterParser;
import com.researchspace.model.collection.RuntimeFieldContext;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ApiV2QuerySupportTest {

  private static final ResourceRegistry REGISTRY =
      new ResourceRegistry(List.of(ApiV2MaintenanceResource.DESCRIPTION));

  @Test
  void limitsTheRawEncodedWhereValue() {
    String encodedAtLimit =
        "%61".repeat(CollectionQueryLimits.MAX_WHERE_LENGTH / 3)
            + "a".repeat(CollectionQueryLimits.MAX_WHERE_LENGTH % 3);

    assertDoesNotThrow(
        () -> ApiV2ResourceRequestParser.validateRawWhere("where=" + encodedAtLimit));
    assertThrows(
        ApiV2BadRequestException.class,
        () ->
            ApiV2ResourceRequestParser.validateRawWhere(
                "unrelated=value&w%68ere=" + encodedAtLimit + "a"));
  }

  @Test
  void limitsTheTotalFilterArguments() {
    String oneThousandIds =
        IntStream.rangeClosed(1, CollectionQueryLimits.MAX_ARGUMENTS)
            .mapToObj(String::valueOf)
            .collect(Collectors.joining(","));
    String oneThousandAndOneIds = oneThousandIds + "," + (CollectionQueryLimits.MAX_ARGUMENTS + 1);

    assertDoesNotThrow(
        () ->
            new RsqlFilterParser(ApiV2MaintenanceResource.DESCRIPTION)
                .parse("id=in=(" + oneThousandIds + ")"));
    assertThrows(
        CollectionQueryException.class,
        () ->
            new RsqlFilterParser(ApiV2MaintenanceResource.DESCRIPTION)
                .parse("id=in=(" + oneThousandAndOneIds + ")"));
  }

  @Test
  void computesPagingCounterWithoutIntegerOverflow() {
    assertEquals(
        214_748_364_601L, ApiV2ListResult.of(List.of(), 0, 100, Integer.MAX_VALUE).pagingCounter());
  }

  @Test
  void reportsNoPrevPageOrNextPageForAnOutOfRangePage() {
    ApiV2ListResult<Object> outOfRange = ApiV2ListResult.of(List.of(), 25, 10, 9);

    assertEquals(3, outOfRange.totalPages());
    assertFalse(outOfRange.hasPrevPage());
    assertFalse(outOfRange.hasNextPage());
    assertNull(outOfRange.prevPage());
    assertNull(outOfRange.nextPage());
  }

  @Test
  void parsesPayloadSortAndAddsStableIdTieBreaker() {
    assertEquals(
        List.of(new Sort("startDate", false), new Sort("message", true), new Sort("id", true)),
        ApiV2SortParser.parse("-startDate,message", ApiV2MaintenanceResource.DESCRIPTION));
    assertEquals(
        List.of(new Sort("id", false)),
        ApiV2SortParser.parse("-id", ApiV2MaintenanceResource.DESCRIPTION));
    assertEquals(
        ApiV2MaintenanceResource.DESCRIPTION.defaultSort(),
        ApiV2SortParser.parse(null, ApiV2MaintenanceResource.DESCRIPTION));
  }

  @Test
  void rejectsUnknownAndEmptySortFields() {
    assertThrows(
        CollectionQueryException.class,
        () -> ApiV2SortParser.parse("unknown", ApiV2MaintenanceResource.DESCRIPTION));
    assertThrows(
        CollectionQueryException.class,
        () -> ApiV2SortParser.parse("message,", ApiV2MaintenanceResource.DESCRIPTION));
    assertThrows(
        CollectionQueryException.class,
        () ->
            ApiV2SortParser.parse(
                "id,startDate,endDate,stopUserLoginDate,message,id",
                ApiV2MaintenanceResource.DESCRIPTION));
  }

  @Test
  void capsLikeAndNotLikePredicatesIndependentlyOfTheGeneralComparisonLimit() {
    String tenPatternComparisons =
        IntStream.range(0, 10)
            .mapToObj(index -> index % 2 == 0 ? "message=like=value" : "message!=*value*")
            .collect(Collectors.joining(";"));
    String elevenPatternComparisons = tenPatternComparisons + ";message=contains=value";

    assertDoesNotThrow(
        () ->
            new RsqlFilterParser(ApiV2MaintenanceResource.DESCRIPTION)
                .parse(tenPatternComparisons));
    assertThrows(
        CollectionQueryException.class,
        () ->
            new RsqlFilterParser(ApiV2MaintenanceResource.DESCRIPTION)
                .parse(elevenPatternComparisons));
  }

  @Test
  void parsesRelationshipTargetFieldsOnBulkRoutes() {
    ResourceRegistry registry =
        new ResourceRegistry(
            List.of(
                ApiV2BookingConfigurationResource.DESCRIPTION,
                ApiV2InstrumentResource.DESCRIPTION,
                ApiV2UserResource.DESCRIPTION));

    assertDoesNotThrow(
        () ->
            ApiV2ResourceRequestParser.bulk(
                "target.name=contains=confocal",
                ApiV2BookingConfigurationResource.DESCRIPTION,
                registry));
  }

  @Test
  void resolvesTheCurrentSubjectOnContextAwareBulkRoutes() {
    ResourceRegistry registry =
        new ResourceRegistry(
            List.of(
                ApiV2BookingConfigurationResource.DESCRIPTION,
                ApiV2InstrumentResource.DESCRIPTION,
                ApiV2UserResource.DESCRIPTION));
    User subject = new User("subject");
    subject.setId(73L);

    ResourceRequest request =
        ApiV2ResourceRequestParser.bulk(
            "createdBy.value==me",
            ApiV2BookingConfigurationResource.DESCRIPTION,
            registry,
            new RuntimeFieldContext(List.of(), subject));

    assertEquals(
        new FilterExpression.Comparison("createdBy.value", Operator.EQUAL, List.of(73L), false),
        request.filter());
    assertThrows(
        CollectionQueryException.class,
        () ->
            ApiV2ResourceRequestParser.bulk(
                "createdBy.value==me", ApiV2BookingConfigurationResource.DESCRIPTION, registry));
  }

  @Test
  void appliesInclusiveAndExclusiveFieldSelectionWhileRetainingId() {
    ScheduledMaintenance maintenance =
        new ScheduledMaintenance(
            Date.from(Instant.parse("2026-07-24T00:00:00Z")),
            Date.from(Instant.parse("2026-07-24T01:00:00Z")));
    maintenance.setId(42L);
    maintenance.setMessage("upgrade");

    Map<String, String> includedFields = new LinkedHashMap<>(Map.of("maintenances", "message"));
    ApiV2FieldsetQuery included = fields(includedFields);
    ApiV2FieldsetQuery excluded = exclude(Map.of("maintenances", "message"));
    ApiV2FieldsetQuery empty = fields(Map.of("maintenances", ""));

    ResourceRequest includedRequest =
        ApiV2ResourceRequestParser.item(
            0, included, ApiV2MaintenanceResource.DESCRIPTION, REGISTRY);
    includedFields.put("maintenances", "startDate");
    ResourceRenderer renderer = new ResourceRenderer(REGISTRY);
    assertEquals(
        Map.of("id", 42L, "message", "upgrade"),
        renderer.render(
            maintenance,
            ApiV2MaintenanceResource.DESCRIPTION,
            includedRequest.fields(),
            includedRequest.includes()));
    ResourceRequest excludedRequest =
        ApiV2ResourceRequestParser.item(
            0, excluded, ApiV2MaintenanceResource.DESCRIPTION, REGISTRY);
    Map<String, Object> excludedDocument =
        renderer.render(
            maintenance,
            ApiV2MaintenanceResource.DESCRIPTION,
            excludedRequest.fields(),
            excludedRequest.includes());
    assertEquals(
        List.of("id", "startDate", "endDate", "stopUserLoginDate", "canUserLoginNow"),
        List.copyOf(excludedDocument.keySet()));
    ResourceRequest emptyRequest =
        ApiV2ResourceRequestParser.item(0, empty, ApiV2MaintenanceResource.DESCRIPTION, REGISTRY);
    assertEquals(
        Map.of("id", 42L),
        renderer.render(
            maintenance,
            ApiV2MaintenanceResource.DESCRIPTION,
            emptyRequest.fields(),
            emptyRequest.includes()));
  }

  @Test
  void rejectsConflictingDuplicateLegacyAndUnknownFieldsets() {
    ApiV2FieldsetQuery mixed = fields(Map.of("maintenances", "message"));
    mixed.setExclude(Map.of("maintenances", "startDate"));
    ApiV2FieldsetQuery unknown = fields(Map.of("maintenances", "unknown"));
    ApiV2FieldsetQuery unknownType = fields(Map.of("unknown", "id"));
    ApiV2FieldsetQuery nestedBrackets = fields(Map.of("maintenances][nested", "message"));
    ApiV2FieldsetQuery duplicate = fields(Map.of("maintenances", "message,message"));

    assertThrows(
        CollectionQueryException.class,
        () ->
            ApiV2ResourceRequestParser.item(
                0, mixed, ApiV2MaintenanceResource.DESCRIPTION, REGISTRY));
    assertThrows(
        CollectionQueryException.class,
        () ->
            ApiV2ResourceRequestParser.item(
                0, unknown, ApiV2MaintenanceResource.DESCRIPTION, REGISTRY));
    assertThrows(
        CollectionQueryException.class,
        () ->
            ApiV2ResourceRequestParser.item(
                0, unknownType, ApiV2MaintenanceResource.DESCRIPTION, REGISTRY));
    assertThrows(
        CollectionQueryException.class,
        () ->
            ApiV2ResourceRequestParser.item(
                0, nestedBrackets, ApiV2MaintenanceResource.DESCRIPTION, REGISTRY));
    assertThrows(
        CollectionQueryException.class,
        () ->
            ApiV2ResourceRequestParser.item(
                0, duplicate, ApiV2MaintenanceResource.DESCRIPTION, REGISTRY));
  }

  private static ApiV2FieldsetQuery fields(Map<String, String> fields) {
    ApiV2FieldsetQuery query = new ApiV2FieldsetQuery();
    query.setFields(fields);
    return query;
  }

  private static ApiV2FieldsetQuery exclude(Map<String, String> exclude) {
    ApiV2FieldsetQuery query = new ApiV2FieldsetQuery();
    query.setExclude(exclude);
    return query;
  }
}
