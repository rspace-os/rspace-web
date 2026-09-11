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
import com.researchspace.model.units.RSUnitDef;
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
 * The seven typed operation endpoints (plan-operations-server-builds.md M6), each posted in the
 * shape frozen in operations-facade-design-m0.md: the created sample and each origin's remaining
 * quantity come back in one envelope (D2), a creating operation answers 201 with a Location,
 * Destroy 200; plus the facade-only rules a unit test cannot reach end to end: every error names a
 * field the caller sent, a stale expectedQuantity is a 409, and the server defaults apply.
 *
 * <p>Not run automatically (extends a real-transaction MVC base).
 */
@WebAppConfiguration
public class InventoryOperationFacadesMVCIT extends API_MVC_InventoryTestBase {

  private static final int GRAM = RSUnitDef.GRAM.getId();
  private static final int CELSIUS = RSUnitDef.CELSIUS.getId();

  private @Autowired SubSampleApiManager subSampleApiManager;

  private User anyUser;
  private String apiKey;

  @BeforeEach
  public void setup() throws Exception {
    super.setUp();
    anyUser = createInitAndLoginAnyUser();
    apiKey = createNewApiKeyForUser(anyUser);
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
    // M0 D7: revive's storageTemp is optional with a server default of 4 degC.
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
  public void aStaleExpectedQuantityIsAConflictThatLeavesTheOriginUntouched() throws Exception {
    // M0 D5: the caller believed the origin held 4 g; it holds 5, so the read was stale.
    ApiSubSample origin = origin();
    post(
        "aliquot",
        "{\"origin\":{\"globalId\":\""
            + origin.getGlobalId()
            + "\",\"amountTaken\":"
            + q("1", GRAM)
            + ",\"expectedQuantity\":"
            + q("4", GRAM)
            + "},\"sampleName\":\"Stale\",\"eachAmount\":"
            + q("0.5", GRAM)
            + "}",
        409);
    assertUnchanged(origin);

    // and a matching expected quantity proceeds
    ApiInventoryOperationResult result =
        created(
            "aliquot",
            "{\"origin\":{\"globalId\":\""
                + origin.getGlobalId()
                + "\",\"amountTaken\":"
                + q("1", GRAM)
                + ",\"expectedQuantity\":"
                + q("5", GRAM)
                + "},\"sampleName\":\"Fresh\",\"eachAmount\":"
                + q("0.5", GRAM)
                + "}");
    assertRemaining(result, origin, "4");
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

  /**
   * Posts a creating operation, asserts 201 with a Location at the new sample, returns the body.
   */
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

  /** The envelope reports the origin's remainder, and it matches what is stored. */
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

  /** One M0 origin element; a null amount leaves the property absent. */
  private static String originJson(ApiSubSample origin, String amountTakenJson) {
    return "{\"globalId\":\""
        + origin.getGlobalId()
        + "\""
        + (amountTakenJson == null ? "" : ",\"amountTaken\":" + amountTakenJson)
        + "}";
  }
}
