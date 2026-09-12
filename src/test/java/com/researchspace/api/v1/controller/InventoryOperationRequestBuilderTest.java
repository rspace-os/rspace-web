package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryOperationAmountMode;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.inventory.InventoryOperationConfigRegistry;
import com.researchspace.service.inventory.InventoryOperationRequestBuilder;
import com.researchspace.service.inventory.InventoryOperationRequestBuilder.DocumentationLink;
import com.researchspace.service.inventory.InventoryOperationRequestBuilder.LabelResolver;
import com.researchspace.service.inventory.InventoryOperationRequestBuilder.Origin;
import com.researchspace.service.inventory.InventoryOperationRequestBuilder.Params;
import com.researchspace.service.inventory.InventoryOperationRequestBuilder.ParentField;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * M1's gate (DevDocs/adr/0007): for each configured operation, the server-side builder must produce
 * exactly the golden request InventoryOperationPostValidatorTest holds - the shape the wizard posts
 * today. Lives in this package to reuse those fixtures verbatim.
 *
 * <p>Two comparisons per operation, because each covers the other's blind spot: Lombok equality
 * (ApiExtraField's equals excludes the inherited name, but sees the WRITE_ONLY newFieldRequest) and
 * JSON-tree equality through the API's own mapper (sees every serialized field, including names,
 * but drops WRITE_ONLY ones).
 *
 * <p>The fixture RESOLVER below reproduces the fixtures' synthetic display names ("IsPartOf
 * SS100"), which are not the shipped catalog's. Real name resolution is covered separately by the
 * production-resolver test, and its non-en-US behaviour is untestable while only en-US ships - the
 * deliberate-review hole the plan calls out.
 */
class InventoryOperationRequestBuilderTest {

  private static final InventoryOperationConfigRegistry REGISTRY =
      new InventoryOperationConfigRegistry();

  private static final ObjectMapper API_MAPPER = Jackson2ObjectMapperBuilder.json().build();

  /** relationType per link-spec key, to rebuild the fixtures' "IsPartOf SS100"-style names. */
  private static final Map<String, String> FIXTURE_RELATION_TYPES =
      Map.of(
          "operations.aliquot.linkFieldName", "IsPartOf",
          "operations.passage.linkFieldName", "IsDerivedFrom",
          "operations.pool.linkFieldName", "HasPart",
          "operations.derive.linkFieldName", "IsDerivedFrom",
          "operations.cryopreserve.linkFieldName", "IsDerivedFrom",
          "operations.revive.linkFieldName", "IsDerivedFrom");

  /**
   * Resolves keys to the names the golden fixtures carry: link names as relationType + " " + the
   * origin's (global-id) name, Destroy's disposed field as its real catalog value, and every other
   * key as itself (the fixtures' text-field names are the raw keys).
   */
  private static final LabelResolver FIXTURE_RESOLVER =
      (key, args) -> {
        String relationType = FIXTURE_RELATION_TYPES.get(key);
        if (relationType != null) {
          return relationType + " " + args.get("originName");
        }
        return "operations.destroy.disposedField".equals(key) ? "Disposed" : key;
      };

  private static ApiQuantityInfo millilitres(String value) {
    return new ApiQuantityInfo(new BigDecimal(value), RSUnitDef.MILLI_LITRE.getId());
  }

  private static ApiQuantityInfo celsius(String value) {
    return new ApiQuantityInfo(new BigDecimal(value), RSUnitDef.CELSIUS.getId());
  }

  /** An origin whose name IS its global id, matching the fixtures' link names. */
  private static Origin origin(long id, String quantityMl, List<ParentField> parentFields) {
    return new Origin(id, "SS" + id, "SS" + id, millilitres(quantityMl), parentFields);
  }

  private static Params.ParamsBuilder params(String operationKey) {
    return Params.builder()
        .operation(REGISTRY.get(operationKey).orElseThrow())
        .resolveLabel(FIXTURE_RESOLVER)
        .clientToday(LocalDate.parse("2026-08-20"));
  }

  private static void assertBuildsTheGoldenRequest(
      ApiInventoryOperationPost fixture, Params params) {
    ApiInventoryOperationPost built = InventoryOperationRequestBuilder.build(params);
    assertEquals(fixture, built, fixture.getOperationType() + ": structural equality");
    assertEquals(
        API_MAPPER.valueToTree(fixture),
        API_MAPPER.valueToTree(built),
        fixture.getOperationType() + ": serialized form (covers field names)");
    Stream<ApiExtraField> generated =
        Stream.concat(
            built.getOrigins().stream().flatMap(o -> o.getExtraFields().stream()),
            built.getNewSample() == null
                ? Stream.empty()
                : built.getNewSample().getExtraFields().stream());
    assertTrue(
        generated.allMatch(ApiExtraField::isNewFieldRequest),
        fixture.getOperationType() + ": every generated field is a new-field request");
  }

  @Test
  void buildsTheAliquotGoldenRequest() {
    assertBuildsTheGoldenRequest(
        InventoryOperationPostValidatorTest.aliquotRequest(),
        params("aliquot")
            .values(
                Map.of(
                    "sampleName",
                    "Aliquots",
                    "count",
                    1,
                    "eachAmount",
                    millilitres("0.5"),
                    "amountTaken",
                    millilitres("0.6")))
            .origins(List.of(origin(100, "10", List.of())))
            .perSubsampleAmounts(Map.of("SS100", millilitres("0.6")))
            .build());
  }

  @Test
  void buildsThePassageGoldenRequest() {
    // The fixture's passage-number text field holds "4": the builder must continue the counter
    // from the origin's parent sample, whose field (matched by operationFieldKey) holds 3.
    assertBuildsTheGoldenRequest(
        InventoryOperationPostValidatorTest.passageRequest(),
        params("passage")
            .values(Map.of("sampleName", "Passaged", "count", 1, "eachAmount", millilitres("0.5")))
            .origins(
                List.of(
                    origin(
                        100,
                        "10",
                        List.of(
                            new ParentField(
                                "Passage number", "3", "operations.passage.numberField")))))
            .build());
  }

  @Test
  void buildsThePoolGoldenRequest() {
    assertBuildsTheGoldenRequest(
        InventoryOperationPostValidatorTest.poolRequest(),
        params("pool")
            .values(Map.of("sampleName", "Pooled", "count", 1, "eachAmount", millilitres("0.5")))
            .origins(List.of(origin(100, "10", List.of()), origin(101, "10", List.of())))
            .perSubsampleAmounts(Map.of("SS100", millilitres("0.6"), "SS101", millilitres("0.7")))
            .build());
  }

  @Test
  void buildsTheDeriveGoldenRequest() {
    assertBuildsTheGoldenRequest(
        InventoryOperationPostValidatorTest.deriveRequest(),
        params("derive")
            .values(
                Map.of(
                    "processName",
                    "PCR",
                    "sampleName",
                    "Derived",
                    "count",
                    1,
                    "eachAmount",
                    millilitres("0.5"),
                    "amountTaken",
                    millilitres("0.6")))
            .origins(List.of(origin(100, "10", List.of())))
            .perSubsampleAmounts(Map.of("SS100", millilitres("0.6")))
            .build());
  }

  @Test
  void buildsTheCryopreserveGoldenRequest() {
    assertBuildsTheGoldenRequest(
        InventoryOperationPostValidatorTest.cryopreserveRequest(),
        params("cryopreserve")
            .values(
                Map.of(
                    "sampleName",
                    "Frozen",
                    "count",
                    1,
                    "eachAmount",
                    millilitres("0.5"),
                    "amountTaken",
                    millilitres("0.6"),
                    "cryomedium",
                    "DMSO 10%",
                    "storageTemp",
                    celsius("-20")))
            .origins(List.of(origin(100, "10", List.of())))
            .perSubsampleAmounts(Map.of("SS100", millilitres("0.6")))
            .build());
  }

  @Test
  void buildsTheReviveGoldenRequest() {
    assertBuildsTheGoldenRequest(
        InventoryOperationPostValidatorTest.reviveRequest(),
        params("revive")
            .values(
                Map.of(
                    "sampleName", "Revived",
                    "count", 1,
                    "eachAmount", millilitres("0.5"),
                    "amountTaken", millilitres("0.6"),
                    "storageTemp", celsius("4")))
            .origins(List.of(origin(100, "10", List.of())))
            .perSubsampleAmounts(Map.of("SS100", millilitres("0.6")))
            .build());
  }

  @Test
  void buildsTheDestroyGoldenRequest() {
    // The one place builder and golden fixture legitimately differ: the fixture predates
    // amountMode and its own suite pins that ABSENT stays accepted (backward compatibility for old
    // clients), but the wizard today sends "all" for an origin-emptying operation
    // (buildOperationRequest.ts, takesWholeOrigin) and InventoryOperationManagerImpl consumes it as
    // the compare-and-swap guard against emptying an origin someone else topped up. So the expected
    // request is the fixture WITH that mode, asserted here rather than papered over in the builder.
    ApiInventoryOperationPost expected = InventoryOperationPostValidatorTest.destroyRequest();
    expected.getOrigins().get(0).setAmountMode(ApiInventoryOperationAmountMode.ALL);
    assertBuildsTheGoldenRequest(
        expected,
        params("destroy").values(Map.of()).origins(List.of(origin(100, "5", List.of()))).build());
  }

  // --- behaviour the golden fixtures do not exercise ---

  @Test
  void appendsTheDocumentationLinkAfterTheProvenanceLinks() {
    ApiInventoryOperationPost built =
        InventoryOperationRequestBuilder.build(
            params("aliquot")
                .values(
                    Map.of(
                        "sampleName",
                        "Aliquots",
                        "count",
                        1,
                        "eachAmount",
                        millilitres("0.5"),
                        "amountTaken",
                        millilitres("0.6")))
                .origins(List.of(origin(100, "10", List.of())))
                .documentationLink(new DocumentationLink("Standard operating procedure", "SD1"))
                .build());
    List<ApiExtraField> fields = built.getNewSample().getExtraFields();
    ApiExtraField documentation = fields.get(fields.size() - 1);
    assertEquals("Standard operating procedure", documentation.getName());
    assertEquals("operations.documentationLink", documentation.getOperationFieldKey());
    assertEquals("IsDocumentedBy", documentation.getLink().getRelationType());
    assertEquals("SD1", documentation.getLink().getTargetGlobalId());
    assertTrue(documentation.isNewFieldRequest());
  }

  @Test
  void collidingPoolLinkNamesAreDisambiguatedByTargetGlobalId() {
    // Two pooled origins sharing the display name "Aliquot" must not yield two identically named
    // fields (the backend rejects duplicates trimmed and case-insensitively).
    ApiInventoryOperationPost built =
        InventoryOperationRequestBuilder.build(
            params("pool")
                .values(
                    Map.of("sampleName", "Pooled", "count", 1, "eachAmount", millilitres("0.5")))
                .origins(
                    List.of(
                        new Origin(100L, "SS100", "Aliquot", millilitres("10"), List.of()),
                        new Origin(101L, "SS101", "Aliquot", millilitres("10"), List.of())))
                .perSubsampleAmounts(
                    Map.of("SS100", millilitres("0.6"), "SS101", millilitres("0.7")))
                .build());
    List<String> names =
        built.getNewSample().getExtraFields().stream().map(ApiExtraField::getName).toList();
    assertEquals(List.of("HasPart Aliquot (SS100)", "HasPart Aliquot (SS101)"), names);
  }

  @Test
  void incrementRestartsFromStartWhenTheParentFieldIsNotACount() {
    ApiInventoryOperationPost built =
        InventoryOperationRequestBuilder.build(
            params("passage")
                .values(
                    Map.of("sampleName", "Passaged", "count", 1, "eachAmount", millilitres("0.5")))
                .origins(
                    List.of(
                        origin(
                            100,
                            "10",
                            List.of(
                                new ParentField(
                                    "Passage number", "three", "operations.passage.numberField")))))
                .build());
    assertEquals("1", passageNumberField(built).getContent());
  }

  @Test
  void incrementFallsBackToMatchingTheParentFieldByLocalizedName() {
    // A hand-created "Passage number" field carries no operationFieldKey; it is matched by the
    // key's resolved name instead (deliberate, see computedValues.ts). The fixture resolver
    // resolves unknown keys to themselves, so the field is named after the key here.
    ApiInventoryOperationPost built =
        InventoryOperationRequestBuilder.build(
            params("passage")
                .values(
                    Map.of("sampleName", "Passaged", "count", 1, "eachAmount", millilitres("0.5")))
                .origins(
                    List.of(
                        origin(
                            100,
                            "10",
                            List.of(
                                new ParentField(" operations.passage.numberField ", "7", null)))))
                .build());
    assertEquals("8", passageNumberField(built).getContent());
  }

  private static ApiExtraField passageNumberField(ApiInventoryOperationPost built) {
    return built.getNewSample().getExtraFields().stream()
        .filter(f -> "operations.passage.numberField".equals(f.getOperationFieldKey()))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void theProductionResolverReadsTheSharedCatalogAndFormatsIcuNamedArguments() {
    // JsonMessageSource loads the same i18next JSON files the wizard uses (copied to the classpath
    // as i18n/locales, namespace "inventory:"); ICU named-argument formatting mirrors i18next-icu.
    LabelResolver resolver =
        InventoryOperationRequestBuilder.messageSourceResolver(
            new JsonMessageSource(), Locale.forLanguageTag("en-US"));
    assertEquals(
        "Pooled from: Vial A",
        resolver.resolve("operations.pool.linkFieldName", Map.of("originName", "Vial A")));
    assertEquals(
        "Is Derived From using process: PCR",
        resolver.resolve(
            "operations.derive.linkFieldName",
            Map.of("processName", "PCR", "originName", "ignored")));
    assertEquals("Disposed", resolver.resolve("operations.destroy.disposedField", Map.of()));
    assertEquals("Derived from", resolver.resolve("operations.aliquot.linkFieldName", Map.of()));
  }

  @Test
  void everyLocaleFallsBackToTheShippedEnUsCatalog() {
    // Only en-US ships, so JsonMessageSource resolves any Accept-Language to the en-US text; the
    // locale still selects ICU's formatting rules. This is the testable half of the locale
    // decision (M0, D1); genuine non-English catalogs have no test until one ships.
    LabelResolver resolver =
        InventoryOperationRequestBuilder.messageSourceResolver(
            new JsonMessageSource(), Locale.GERMANY);
    assertEquals(
        "Pooled from: Vial A",
        resolver.resolve("operations.pool.linkFieldName", Map.of("originName", "Vial A")));
  }
}
