package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryOperationResult;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.apiutils.ApiError;
import com.researchspace.model.User;
import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.service.inventory.SubSampleApiManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The seven typed operation endpoints (DevDocs/adr/0011 M6), each posted in the shape frozen in
 * DevDocs/adr/0011: the created sample and each origin's remaining quantity come back in one
 * envelope (D2), a creating operation answers 201 with a Location, Destroy 200; plus the
 * facade-only rules a unit test cannot reach end to end: every error names a field the caller sent,
 * and the server defaults apply.
 *
 * <p>Not run automatically (extends a real-transaction MVC base).
 */
@WebAppConfiguration
public class InventoryOperationFacadesMVCIT extends API_MVC_InventoryTestBase {

  private static final int GRAM = RSUnitDef.GRAM.getId();
  private static final int CELSIUS = RSUnitDef.CELSIUS.getId();
  private static final int MILLIGRAM = RSUnitDef.MILLI_GRAM.getId();

  private @Autowired SubSampleApiManager subSampleApiManager;

  private @Autowired SystemPropertyManager systemPropertyManager;

  private User anyUser;
  private String apiKey;

  @BeforeEach
  public void setup() throws Exception {
    super.setUp();
    anyUser = createInitAndLoginAnyUser();
    apiKey = createNewApiKeyForUser(anyUser);
    enableOperations();
  }

  /**
   * RSDEV-1231 seeds {@code inventory.operations.available} DENIED, so each test turns it on first:
   * what is under test in this class is the operation, not the toggle.
   */
  private void enableOperations() {
    systemPropertyManager.save(
        SystemPropertyName.INVENTORY_OPERATIONS_AVAILABLE,
        HierarchicalPermission.ALLOWED,
        getSysAdminUser());
  }

  // --- the seven happy paths ---

  @Test
  public void aliquotCreatesTwoChildrenAndReportsTheOriginsRemainder() throws Exception {
    ApiSubSample origin = origin();
    ApiInventoryOperationResult result =
        created(
            "aliquot",
            "{\"origin\":"
                + originJson(origin, q("1", GRAM))
                + ",\"sampleName\":\"Aliquots\",\"count\":2,\"eachAmount\":"
                + q("0.5", GRAM)
                + "}");
    assertEquals("Aliquots", result.getSample().getName());
    assertEquals(2, result.getSample().getSubSamples().size());
    assertRemaining(result, origin, "4");
  }

  @Test
  public void passageTakesNothingFromTheOrigin() throws Exception {
    ApiSubSample origin = origin();
    ApiInventoryOperationResult result =
        created(
            "passage",
            "{\"origin\":"
                + originJson(origin, null)
                + ",\"sampleName\":\"HeLa p3\",\"eachAmount\":"
                + q("0.5", GRAM)
                + "}");
    assertEquals("HeLa p3", result.getSample().getName());
    assertRemaining(result, origin, "5");
  }

  @Test
  public void poolTakesEachOriginsOwnAmount() throws Exception {
    ApiSubSample first = origin();
    ApiSubSample second = origin();
    ApiInventoryOperationResult result =
        created(
            "pool",
            "{\"origins\":["
                + originJson(first, q("1", GRAM))
                + ","
                + originJson(second, q("2", GRAM))
                + "],\"sampleName\":\"Pooled\",\"count\":1,\"eachAmount\":"
                + q("3", GRAM)
                + "}");
    assertEquals(2, result.getOrigins().size());
    assertEquals(first.getId(), result.getOrigins().get(0).getId(), "request order");
    assertEquals(0, new BigDecimal("4").compareTo(quantityOf(result.getOrigins().get(0))));
    assertEquals(0, new BigDecimal("3").compareTo(quantityOf(result.getOrigins().get(1))));
  }

  @Test
  public void poolWithTakeAllEmptiesEveryOriginWithoutAnyAmountBeingSent() throws Exception {
    ApiSubSample first = origin();
    ApiSubSample second = origin();
    ApiInventoryOperationResult result =
        created(
            "pool",
            "{\"origins\":["
                + originJson(first, null)
                + ","
                + originJson(second, null)
                + "],\"takeAll\":true,\"sampleName\":\"Everything\",\"eachAmount\":"
                + q("10", GRAM)
                + "}");
    assertRemaining(result, first, "0");
    assertRemaining(result, second, "0");
  }

  @Test
  public void deriveNamesTheProcessInTheProvenanceLink() throws Exception {
    ApiSubSample origin = origin();
    ApiInventoryOperationResult result =
        created(
            "derive",
            "{\"origin\":"
                + originJson(origin, q("0.6", GRAM))
                + ",\"processName\":\"PCR\",\"sampleName\":\"Derived\",\"count\":1,\"eachAmount\":"
                + q("0.5", GRAM)
                + "}");
    assertTrue(
        sampleApiMgr.getApiSampleById(result.getSample().getId(), anyUser).getExtraFields().stream()
            .anyMatch(field -> "Is Derived From using process: PCR".equals(field.getName())),
        "the server built the link from the process name input");
    assertRemaining(result, origin, "4.4");
  }

  @Test
  public void cryopreserveStoresTheTemperatureAndMedium() throws Exception {
    ApiSubSample origin = origin();
    ApiInventoryOperationResult result =
        created(
            "cryopreserve",
            "{\"origin\":"
                + originJson(origin, q("0.6", GRAM))
                + ",\"sampleName\":\"Frozen\",\"count\":1,\"eachAmount\":"
                + q("0.5", GRAM)
                + ",\"cryomedium\":\"DMSO 10%\",\"storageTemp\":"
                + q("-80", CELSIUS)
                + "}");
    assertEquals(
        0,
        new BigDecimal("-80").compareTo(result.getSample().getStorageTempMin().getNumericValue()));
    assertRemaining(result, origin, "4.4");
  }

  @Test
  public void reviveWithoutAStorageTempGetsTheServerDefault() throws Exception {
    ApiSubSample origin = origin();
    ApiInventoryOperationResult result =
        created(
            "revive",
            "{\"origin\":"
                + originJson(origin, q("0.6", GRAM))
                + ",\"sampleName\":\"Revived\",\"count\":1,\"eachAmount\":"
                + q("0.5", GRAM)
                + "}");
    ApiQuantityInfo storageTemp = result.getSample().getStorageTempMin();
    assertEquals(0, new BigDecimal("4").compareTo(storageTemp.getNumericValue()));
    assertEquals(CELSIUS, storageTemp.getUnitId());
    assertRemaining(result, origin, "4.4");
  }

  @Test
  public void destroyAnswers200EmptiesTheOriginAndCreatesNothing() throws Exception {
    ApiSubSample origin = origin();
    MvcResult response = post("destroy", "{\"origin\":" + originJson(origin, null) + "}", 200);
    assertNull(response.getResponse().getHeader(HttpHeaders.LOCATION));
    ApiInventoryOperationResult result =
        getFromJsonResponseBody(response, ApiInventoryOperationResult.class);
    assertNull(result.getSample());
    assertRemaining(result, origin, "0");
    ApiSubSample after = subSampleApiManager.getApiSubSampleById(origin.getId(), anyUser);
    assertTrue(
        after.getExtraFields().stream().map(ApiExtraField::getName).anyMatch("Disposed"::equals),
        () -> "expected the disposed date on the origin, got " + after.getExtraFields());
  }

  // --- the facade-only rules ---

  @Test
  public void anAbsentCountDefaultsToOneChild() throws Exception {
    ApiInventoryOperationResult result =
        created(
            "aliquot",
            "{\"origin\":"
                + originJson(origin(), q("1", GRAM))
                + ",\"sampleName\":\"One\",\"eachAmount\":"
                + q("0.5", GRAM)
                + "}");
    assertEquals(1, result.getSample().getSubSamples().size());
  }

  @Test
  public void aSingleOriginErrorNamesTheFieldTheCallerSent() throws Exception {
    // The core reports the live-state rule under origins[0]; an aliquot client sent origin.
    ApiSubSample origin = origin();
    List<String> errors =
        errorsOf(
            post(
                "aliquot",
                "{\"origin\":"
                    + originJson(origin, q("50", GRAM))
                    + ",\"sampleName\":\"Too much\",\"eachAmount\":"
                    + q("0.5", GRAM)
                    + "}",
                400));
    assertTrue(
        errors.stream().anyMatch(message -> message.startsWith("origin.amountTaken:")),
        () -> "expected origin.amountTaken, got " + errors);
    assertTrue(
        errors.stream().noneMatch(message -> message.contains("origins[")), errors::toString);
    assertUnchanged(origin);
  }

  @Test
  public void aPoolErrorKeepsTheIndexAndNamesTheGlobalId() throws Exception {
    ApiSubSample origin = origin();
    List<String> errors =
        errorsOf(
            post(
                "pool",
                "{\"origins\":["
                    + originJson(origin, q("1", GRAM))
                    + ","
                    + originJson(origin, q("1", GRAM))
                    + "],\"sampleName\":\"Twice\",\"eachAmount\":"
                    + q("2", GRAM)
                    + "}",
                400));
    assertTrue(
        errors.stream().anyMatch(message -> message.startsWith("origins[1].globalId:")),
        () -> "expected origins[1].globalId, got " + errors);
    assertUnchanged(origin);
  }

  @Test
  public void poolWithTakeAllRefusesAnOriginThatAlsoSendsAnAmount() throws Exception {
    ApiSubSample first = origin();
    ApiSubSample second = origin();
    List<String> errors =
        errorsOf(
            post(
                "pool",
                "{\"origins\":["
                    + originJson(first, null)
                    + ","
                    + originJson(second, q("1", GRAM))
                    + "],\"takeAll\":true,\"sampleName\":\"Contradictory\",\"eachAmount\":"
                    + q("10", GRAM)
                    + "}",
                400));
    // Only the resolved text distinguishes amountTakenNotWithTakeAll from the generic
    // amountTakenNotApplicable, and naming the caller's own choice is the point of the separate
    // code.
    assertTrue(
        errors.stream()
            .anyMatch(
                message ->
                    message.startsWith("origins[1].amountTaken:") && message.contains("takeAll")),
        () -> "expected origins[1].amountTaken naming takeAll, got " + errors);
    assertUnchanged(first);
    assertUnchanged(second);
  }

  @Test
  public void anOriginThatIsNotASubsampleIsRejectedAtBinding() throws Exception {
    ApiSubSample origin = origin();
    String sampleGlobalId = "SA" + origin.getSampleInfo().getId();
    List<String> errors =
        errorsOf(post("destroy", "{\"origin\":{\"globalId\":\"" + sampleGlobalId + "\"}}", 400));
    assertTrue(
        errors.stream().anyMatch(message -> message.startsWith("origin.globalId:")),
        () -> "expected origin.globalId, got " + errors);
    assertUnchanged(origin);
  }

  @Test
  public void aMissingRequiredInputNamesTheInputKey() throws Exception {
    ApiSubSample origin = origin();
    List<String> errors =
        errorsOf(
            post(
                "derive",
                "{\"origin\":"
                    + originJson(origin, q("1", GRAM))
                    + ",\"sampleName\":\"No process\",\"eachAmount\":"
                    + q("0.5", GRAM)
                    + "}",
                400));
    assertTrue(
        errors.stream().anyMatch(message -> message.startsWith("processName:")),
        () -> "expected processName, got " + errors);
    assertUnchanged(origin);
  }

  @Test
  public void aFractionalCountIsRejectedNamingTheFieldRatherThanSilentlyCoerced() throws Exception {
    // Asserted through the running converter, not the DTO: the coercion this guards against is
    // Jackson's, and only a request that actually crosses the HTTP boundary exercises the mapper
    // the endpoint is wired to.
    ApiSubSample origin = origin();
    List<String> errors =
        errorsOf(
            post(
                "aliquot",
                "{\"origin\":"
                    + originJson(origin, q("1", GRAM))
                    + ",\"sampleName\":\"Fractional\",\"count\":1.9,\"eachAmount\":"
                    + q("0.5", GRAM)
                    + "}",
                400));
    assertTrue(
        errors.stream().anyMatch(message -> message.startsWith("count:")),
        () -> "expected count, got " + errors);
    assertUnchanged(origin);
  }

  @Test
  public void anAmountTakenTheOriginsOwnUnitCannotExpressRedenominatesTheOrigin() throws Exception {
    // 4999.999 mg from a 5 g origin leaves 0.000001 g, which the origin's own unit cannot store.
    // It IS 0.001 mg, and the descent stops at the FIRST unit that fits, so the remainder is stored
    // in milligrams, not micrograms, rather than the operation being refused. This is the
    // end-to-end version: over HTTP, through the real validator, the real decrement and the real
    // column.
    ApiSubSample origin = origin();

    ApiInventoryOperationResult result =
        created(
            "aliquot",
            "{\"origin\":"
                + originJson(origin, q("4999.999", MILLIGRAM))
                + ",\"sampleName\":\"Down to micrograms\",\"eachAmount\":"
                + q("0.5", GRAM)
                + "}");

    ApiSubSample remaining =
        result.getOrigins().stream()
            .filter(o -> o.getId().equals(origin.getId()))
            .findFirst()
            .orElseThrow();
    assertEquals(MILLIGRAM, remaining.getQuantity().getUnitId(), "remainder re-denominated");
    assertEquals(0, new BigDecimal("0.001").compareTo(quantityOf(remaining)), "reported remainder");
    ApiSubSample stored = subSampleApiManager.getApiSubSampleById(origin.getId(), anyUser);
    assertEquals(MILLIGRAM, stored.getQuantity().getUnitId(), "stored in the finer unit");
    assertEquals(0, new BigDecimal("0.001").compareTo(quantityOf(stored)), "stored remainder");
  }

  @Test
  public void aPerSubsampleAmountWhoseTotalTheColumnCannotHoldIsRejected() throws Exception {
    // Each of the two children fits the DECIMAL(19,3) quantity column on its own; the parent total
    // the created sample recalculates from them does not. That recompute happens during
    // persistence, so the request passed validation, decremented the origins, and then failed
    // inside the transaction as a 500.
    ApiSubSample origin = origin();
    List<String> errors =
        errorsOf(
            post(
                "passage",
                "{\"origin\":"
                    + originJson(origin, null)
                    + ",\"sampleName\":\"Overflowing\",\"count\":2,\"eachAmount\":"
                    + q("6E+15", GRAM)
                    + "}",
                400));
    assertTrue(
        errors.stream().anyMatch(message -> message.startsWith("eachAmount:")),
        () -> "expected eachAmount, got " + errors);
    assertUnchanged(origin);
  }

  @Test
  public void anEachAmountCarryingOnlyAUnitIsRejectedRatherThanCreatingEmptyStock()
      throws Exception {
    // @NotNull asserts eachAmount is present, not that it carries a number, so {"unitId":..} alone
    // passed every rule; the null was copied into each created subsample while the origin was
    // still decremented.
    ApiSubSample origin = origin();
    List<String> errors =
        errorsOf(
            post(
                "aliquot",
                "{\"origin\":"
                    + originJson(origin, q("1", GRAM))
                    + ",\"sampleName\":\"Numberless\",\"eachAmount\":{\"unitId\":"
                    + GRAM
                    + "}}",
                400));
    assertTrue(
        errors.stream().anyMatch(message -> message.startsWith("eachAmount:")),
        () -> "expected eachAmount, got " + errors);
    assertUnchanged(origin);
  }

  @Test
  public void aStorageTempCarryingOnlyAUnitIsRejectedRatherThanFailingDuringPersistence()
      throws Exception {
    // The sample's own @ValidTemperature treats a number-less temperature as unresolved and lets
    // it through, so this reached the INSERT and came back as a 500.
    ApiSubSample origin = origin();
    List<String> errors =
        errorsOf(
            post(
                "cryopreserve",
                "{\"origin\":"
                    + originJson(origin, q("1", GRAM))
                    + ",\"sampleName\":\"Numberless\",\"count\":1,\"eachAmount\":"
                    + q("0.5", GRAM)
                    + ",\"storageTemp\":{\"unitId\":"
                    + CELSIUS
                    + "}}",
                400));
    assertTrue(
        errors.stream().anyMatch(message -> message.startsWith("storageTemp:")),
        () -> "expected storageTemp, got " + errors);
    assertUnchanged(origin);
  }

  @Test
  public void aStorageTempBelowAbsoluteZeroIsRejectedThoughItIsUnderTheOperationsCeiling()
      throws Exception {
    // Cryopreserve sets no lower bound, so -300 C sat under its -18 C ceiling and passed here;
    // only the entity's @ValidTemperature caught it, once the transaction was already writing.
    ApiSubSample origin = origin();
    List<String> errors =
        errorsOf(
            post(
                "cryopreserve",
                "{\"origin\":"
                    + originJson(origin, q("1", GRAM))
                    + ",\"sampleName\":\"Impossible\",\"count\":1,\"eachAmount\":"
                    + q("0.5", GRAM)
                    + ",\"storageTemp\":"
                    + q("-300", CELSIUS)
                    + "}",
                400));
    assertTrue(
        errors.stream().anyMatch(message -> message.startsWith("storageTemp:")),
        () -> "expected storageTemp, got " + errors);
    assertUnchanged(origin);
  }

  @Test
  public void anUnreadableDocumentationTargetNamesTheFieldTheCallerSent() throws Exception {
    // Resolved only while the built sample's link was created, deep in the transaction, this came
    // back as a bare 422 with no field path on it.
    ApiSubSample origin = origin();
    List<String> errors =
        errorsOf(
            post(
                "aliquot",
                "{\"origin\":"
                    + originJson(origin, q("1", GRAM))
                    + ",\"sampleName\":\"Documented\",\"eachAmount\":"
                    + q("0.5", GRAM)
                    + ",\"documentedByGlobalId\":\"SD999999999\"}",
                400));
    assertTrue(
        errors.stream().anyMatch(message -> message.startsWith("documentedByGlobalId:")),
        () -> "expected documentedByGlobalId, got " + errors);
    assertUnchanged(origin);
  }

  @Test
  public void oneSubsampleNamedTwiceUnderDifferentSpellingsTakesNothingFromIt() throws Exception {
    // SS<id> and SS<id>v1 name one subsample. The duplicate check deduped on the raw string, so
    // both spellings got past it, each was checked against the origin's pre-operation quantity,
    // and each was decremented: 5 g lost 2 g to a request that asked for 1 g.
    ApiSubSample origin = origin();
    List<String> errors =
        errorsOf(
            post(
                "pool",
                "{\"origins\":["
                    + originJson(origin.getGlobalId(), q("1", GRAM))
                    + ","
                    + originJson(origin.getGlobalId() + "v1", q("1", GRAM))
                    + "],\"sampleName\":\"Aliased\",\"count\":1,\"eachAmount\":"
                    + q("2", GRAM)
                    + "}",
                400));
    assertTrue(
        errors.stream().anyMatch(message -> message.startsWith("origins[1].globalId:")),
        () -> "expected origins[1].globalId, got " + errors);
    assertUnchanged(origin);
  }

  // --- helpers ---

  /** A fresh 5 g origin. */
  private ApiSubSample origin() {
    return createBasicSampleForUser(anyUser).getSubSamples().get(0);
  }

  private MvcResult post(String operation, String json, int expectedStatus) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/operations/" + operation, anyUser, json))
            .andReturn();
    assertEquals(
        expectedStatus,
        result.getResponse().getStatus(),
        () -> "unexpected response to " + json + ": " + responseBody(result));
    return result;
  }

  private static String responseBody(MvcResult result) {
    try {
      return result.getResponse().getContentAsString();
    } catch (Exception unreadable) {
      return "<unreadable body: " + unreadable + ">";
    }
  }

  private ApiInventoryOperationResult created(String operation, String json) throws Exception {
    MvcResult response = post(operation, json, 201);
    ApiInventoryOperationResult result =
        getFromJsonResponseBody(response, ApiInventoryOperationResult.class);
    assertNotNull(result.getSample(), "a creating operation returns the sample");
    String location = response.getResponse().getHeader(HttpHeaders.LOCATION);
    assertNotNull(location, "201 carries a Location");
    assertTrue(
        location.endsWith("/api/inventory/v1/samples/" + result.getSample().getId()),
        () -> "Location points elsewhere: " + location);
    mockMvc
        .perform(
            createBuilderForGet(
                API_VERSION.ONE, apiKey, "/samples/{id}", anyUser, result.getSample().getId()))
        .andExpect(status().isOk());
    return result;
  }

  private static BigDecimal quantityOf(ApiSubSample subSample) {
    return subSample.getQuantity().getNumericValue();
  }

  private void assertRemaining(
      ApiInventoryOperationResult result, ApiSubSample origin, String expected) {
    ApiSubSample reported =
        result.getOrigins().stream()
            .filter(o -> o.getId().equals(origin.getId()))
            .findFirst()
            .orElseThrow();
    assertEquals(0, new BigDecimal(expected).compareTo(quantityOf(reported)), "reported remainder");
    ApiSubSample stored = subSampleApiManager.getApiSubSampleById(origin.getId(), anyUser);
    assertEquals(0, quantityOf(reported).compareTo(quantityOf(stored)), "stored remainder");
  }

  private void assertUnchanged(ApiSubSample origin) {
    ApiSubSample reloaded = subSampleApiManager.getApiSubSampleById(origin.getId(), anyUser);
    assertEquals(0, quantityOf(origin).compareTo(quantityOf(reloaded)), "origin must be untouched");
  }

  private List<String> errorsOf(MvcResult result) throws Exception {
    return getErrorFromJsonResponseBody(result, ApiError.class).getErrors();
  }

  private static String q(String value, int unitId) {
    return "{\"numericValue\":" + value + ",\"unitId\":" + unitId + "}";
  }

  private static String originJson(ApiSubSample origin, String amountTakenJson) {
    return originJson(origin.getGlobalId(), amountTakenJson);
  }

  private static String originJson(String globalId, String amountTakenJson) {
    return "{\"globalId\":\""
        + globalId
        + "\""
        + (amountTakenJson == null ? "" : ",\"amountTaken\":" + amountTakenJson)
        + "}";
  }
}
