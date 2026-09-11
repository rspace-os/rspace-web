package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.ApiSubSampleInfo;
import com.researchspace.apiutils.ApiError;
import com.researchspace.model.User;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.SubSampleApiManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

/**
 * M3's gate (plan-operations-server-builds.md): for each of the seven configured operations, the
 * server-built shape ({@code inputs}) must persist records identical to those the client-assembled
 * shape ({@code newSample}) persists. The client-assembled bodies here are what the wizard sends
 * today, transcribed from buildOperationRequest.ts including the parts M1's builder deliberately
 * omits (amountMode "explicit", the aggregate newSample.quantity, templateId null, empty subsample
 * extraFields), so an identical fingerprint is the first real test of the claim that those parts
 * have no consumer.
 *
 * <p>Not run automatically (extends a real-transaction MVC base).
 */
@WebAppConfiguration
public class InventoryOperationsInputsShapeMVCIT extends API_MVC_InventoryTestBase {

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

  @Test
  public void aliquotPersistsIdenticalRecordsFromEitherShape() throws Exception {
    assertIdenticalRecords(
        1,
        o ->
            oldShape(
                "aliquot",
                origin(o.get(0), "explicit", q("1", GRAM), null),
                "Aliquots",
                2,
                q("0.5", GRAM),
                q("1", GRAM),
                link("Derived from", "operations.aliquot.linkFieldName", "IsPartOf", o.get(0)),
                null),
        o ->
            newShape(
                "aliquot",
                origin(o.get(0), "explicit", q("1", GRAM), null),
                "{\"sampleName\":\"Aliquots\",\"count\":2,\"eachAmount\":" + q("0.5", GRAM) + "}"));
  }

  @Test
  public void passagePersistsIdenticalRecordsFromEitherShape() throws Exception {
    // The origin's parent carries no passage counter, so both shapes start it at 1.
    assertIdenticalRecords(
        1,
        o ->
            oldShape(
                "passage",
                origin(o.get(0), "explicit", q("0", GRAM), null),
                "Passaged",
                1,
                q("0.5", GRAM),
                q("0.5", GRAM),
                link("Passaged from", "operations.passage.linkFieldName", "IsDerivedFrom", o.get(0))
                    + ","
                    + text("Passage number", "operations.passage.numberField", "1"),
                null),
        o ->
            newShape(
                "passage",
                origin(o.get(0), "explicit", q("0", GRAM), null),
                "{\"sampleName\":\"Passaged\",\"count\":1,\"eachAmount\":" + q("0.5", GRAM) + "}"));
  }

  @Test
  public void poolPersistsIdenticalRecordsFromEitherShape() throws Exception {
    // Both origins are named mySubSample, so the wizard disambiguates the two link names by target.
    assertIdenticalRecords(
        2,
        o ->
            oldShape(
                "pool",
                origin(o.get(0), "explicit", q("1", GRAM), null)
                    + ","
                    + origin(o.get(1), "explicit", q("1", GRAM), null),
                "Pooled",
                1,
                q("2", GRAM),
                q("2", GRAM),
                link(
                        "Pooled from: " + o.get(0).getName() + " (" + o.get(0).getGlobalId() + ")",
                        "operations.pool.linkFieldName",
                        "HasPart",
                        o.get(0))
                    + ","
                    + link(
                        "Pooled from: " + o.get(1).getName() + " (" + o.get(1).getGlobalId() + ")",
                        "operations.pool.linkFieldName",
                        "HasPart",
                        o.get(1)),
                null),
        o ->
            newShape(
                "pool",
                origin(o.get(0), "explicit", q("1", GRAM), null)
                    + ","
                    + origin(o.get(1), "explicit", q("1", GRAM), null),
                "{\"sampleName\":\"Pooled\",\"count\":1,\"eachAmount\":" + q("2", GRAM) + "}"));
  }

  @Test
  public void derivePersistsIdenticalRecordsFromEitherShape() throws Exception {
    assertIdenticalRecords(
        1,
        o ->
            oldShape(
                "derive",
                origin(o.get(0), "explicit", q("0.6", GRAM), null),
                "Derived",
                1,
                q("0.5", GRAM),
                q("0.5", GRAM),
                link(
                    "Is Derived From using process: PCR",
                    "operations.derive.linkFieldName",
                    "IsDerivedFrom",
                    o.get(0)),
                null),
        o ->
            newShape(
                "derive",
                origin(o.get(0), "explicit", q("0.6", GRAM), null),
                "{\"processName\":\"PCR\",\"sampleName\":\"Derived\",\"count\":1,\"eachAmount\":"
                    + q("0.5", GRAM)
                    + "}"));
  }

  @Test
  public void cryopreservePersistsIdenticalRecordsFromEitherShape() throws Exception {
    assertIdenticalRecords(
        1,
        o ->
            oldShape(
                "cryopreserve",
                origin(o.get(0), "explicit", q("0.6", GRAM), null),
                "Frozen",
                1,
                q("0.5", GRAM),
                q("0.5", GRAM),
                link(
                        "Frozen from",
                        "operations.cryopreserve.linkFieldName",
                        "IsDerivedFrom",
                        o.get(0))
                    + ","
                    + text("Cryomedium", "operations.cryopreserve.cryomediumField", "DMSO 10%"),
                q("-80", CELSIUS)),
        o ->
            newShape(
                "cryopreserve",
                origin(o.get(0), "explicit", q("0.6", GRAM), null),
                "{\"sampleName\":\"Frozen\",\"count\":1,\"eachAmount\":"
                    + q("0.5", GRAM)
                    + ",\"cryomedium\":\"DMSO 10%\",\"storageTemp\":"
                    + q("-80", CELSIUS)
                    + "}"));
  }

  @Test
  public void revivePersistsIdenticalRecordsFromEitherShape() throws Exception {
    assertIdenticalRecords(
        1,
        o ->
            oldShape(
                "revive",
                origin(o.get(0), "explicit", q("0.6", GRAM), null),
                "Revived",
                1,
                q("0.5", GRAM),
                q("0.5", GRAM),
                link("Revived from", "operations.revive.linkFieldName", "IsDerivedFrom", o.get(0)),
                q("4", CELSIUS)),
        o ->
            newShape(
                "revive",
                origin(o.get(0), "explicit", q("0.6", GRAM), null),
                "{\"sampleName\":\"Revived\",\"count\":1,\"eachAmount\":"
                    + q("0.5", GRAM)
                    + ",\"storageTemp\":"
                    + q("4", CELSIUS)
                    + "}"));
  }

  @Test
  public void destroyPersistsIdenticalRecordsFromEitherShape() throws Exception {
    // The wizard stamps the disposed date from the browser clock; the server-built path resolves
    // "today" in the session's timezone, which for an API-key session is the server's.
    String disposed =
        text("Disposed", "operations.destroy.disposedField", LocalDate.now().toString());
    assertIdenticalRecords(
        1,
        o ->
            "{\"operationType\":\"destroy\",\"origins\":["
                + origin(o.get(0), "all", q("5", GRAM), "[" + disposed + "]")
                + "],\"newSample\":null}",
        o -> newShape("destroy", origin(o.get(0), "all", q("5", GRAM), null), "{}"));
  }

  @Test
  public void aMissingRequiredInputIsAFieldScoped400NamingTheInputKey() throws Exception {
    ApiSubSample origin = origins(1).get(0);
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey,
                    "/operations",
                    anyUser,
                    newShape(
                        "aliquot",
                        origin(origin, "explicit", q("1", GRAM), null),
                        "{\"count\":2,\"eachAmount\":" + q("0.5", GRAM) + "}")))
            .andExpect(status().isBadRequest())
            .andReturn();
    List<String> messages = getErrorFromJsonResponseBody(result, ApiError.class).getErrors();
    assertTrue(
        messages.stream().anyMatch(message -> message.startsWith("sampleName:")),
        () -> "expected an error on sampleName, got " + messages);
    ApiSubSample reloaded = subSampleApiManager.getApiSubSampleById(origin.getId(), anyUser);
    assertEquals(
        0,
        origin.getQuantity().getNumericValue().compareTo(reloaded.getQuantity().getNumericValue()));
  }

  // --- helpers ---

  /**
   * Performs the client-assembled body against one fresh set of origins and the server-built body
   * against another, then asserts the persisted records (created sample, its fields and subsamples,
   * each origin's quantity and fields) are identical once origin global ids are normalised.
   */
  private void assertIdenticalRecords(
      int originCount,
      Function<List<ApiSubSample>, String> clientAssembled,
      Function<List<ApiSubSample>, String> serverBuilt)
      throws Exception {
    List<ApiSubSample> oldOrigins = origins(originCount);
    String oldRecords = fingerprint(perform(clientAssembled.apply(oldOrigins)), oldOrigins);
    List<ApiSubSample> newOrigins = origins(originCount);
    String newRecords = fingerprint(perform(serverBuilt.apply(newOrigins)), newOrigins);
    assertFalse(oldRecords.isBlank(), "the fingerprint must describe something");
    assertEquals(oldRecords, newRecords);
  }

  /** Fresh 5 g origins, one per basic sample, all named mySubSample. */
  private List<ApiSubSample> origins(int count) {
    List<ApiSubSample> origins = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      origins.add(createBasicSampleForUser(anyUser).getSubSamples().get(0));
    }
    return origins;
  }

  /** Posts the body, expects 201, returns the created sample's id (null for Destroy). */
  private Long perform(String operationJson) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/operations", anyUser, operationJson))
            .andReturn();
    assertEquals(
        201,
        result.getResponse().getStatus(),
        () -> "unexpected response to " + operationJson + ": " + responseBody(result));
    if (result.getResponse().getContentAsString().isBlank()) {
      return null;
    }
    return getFromJsonResponseBody(result, ApiSampleWithFullSubSamples.class).getId();
  }

  private static String responseBody(MvcResult result) {
    try {
      return result.getResponse().getContentAsString();
    } catch (Exception unreadable) {
      return "<unreadable body: " + unreadable + ">";
    }
  }

  private String fingerprint(Long sampleId, List<ApiSubSample> origins) throws Exception {
    StringBuilder out = new StringBuilder();
    if (sampleId != null) {
      ApiSample sample = sampleApiMgr.getApiSampleById(sampleId, anyUser);
      out.append("sample ")
          .append(sample.getName())
          .append(" total=")
          .append(q(sample.getQuantity()))
          .append(" template=")
          .append(sample.getTemplateId())
          .append(" storage=")
          .append(q(sample.getStorageTempMin()))
          .append("..")
          .append(q(sample.getStorageTempMax()))
          .append('\n');
      for (ApiExtraField field : sample.getExtraFields()) {
        out.append("  field ").append(describe(field)).append('\n');
      }
      for (ApiSubSampleInfo info : sample.getSubSamples()) {
        ApiSubSample subSample = subSampleApiManager.getApiSubSampleById(info.getId(), anyUser);
        out.append("  subsample ")
            .append(subSample.getName())
            .append(" quantity=")
            .append(q(subSample.getQuantity()))
            .append(" fields=")
            .append(subSample.getExtraFields().size())
            .append('\n');
      }
    }
    for (ApiSubSample origin : origins) {
      ApiSubSample after = subSampleApiManager.getApiSubSampleById(origin.getId(), anyUser);
      out.append("origin quantity=").append(q(after.getQuantity())).append('\n');
      for (ApiExtraField field : after.getExtraFields()) {
        out.append("  field ").append(describe(field)).append('\n');
      }
    }
    String records = out.toString();
    for (int i = 0; i < origins.size(); i++) {
      records =
          records.replaceAll(
              "\\b" + Pattern.quote(origins.get(i).getGlobalId()) + "\\b", "ORIGIN" + i);
    }
    return records;
  }

  private static String q(ApiQuantityInfo quantity) {
    return quantity == null
        ? "none"
        : quantity.getNumericValue().stripTrailingZeros().toPlainString()
            + "/"
            + quantity.getUnitId();
  }

  private static String describe(ApiExtraField field) {
    return field.getName()
        + "|"
        + field.getType()
        + "|"
        + field.getContent()
        + "|"
        + field.getOperationFieldKey()
        + "|"
        + (field.getLink() == null
            ? ""
            : field.getLink().getRelationType() + "->" + field.getLink().getTargetGlobalId());
  }

  private static String q(String value, int unitId) {
    return "{\"numericValue\":" + value + ",\"unitId\":" + unitId + "}";
  }

  private static String link(String name, String key, String relationType, ApiSubSample target) {
    return "{\"name\":\""
        + name
        + "\",\"type\":\"link\",\"newFieldRequest\":true,\"operationFieldKey\":\""
        + key
        + "\",\"link\":{\"relationType\":\""
        + relationType
        + "\",\"targetGlobalId\":\""
        + target.getGlobalId()
        + "\",\"versionPin\":null}}";
  }

  private static String text(String name, String key, String content) {
    return "{\"name\":\""
        + name
        + "\",\"type\":\"text\",\"newFieldRequest\":true,\"operationFieldKey\":\""
        + key
        + "\",\"content\":\""
        + content
        + "\"}";
  }

  /** One origin element, as both shapes send it; extraFieldsJson is a JSON array or null. */
  private static String origin(
      ApiSubSample origin, String amountMode, String amountTakenJson, String extraFieldsJson) {
    return "{\"id\":"
        + origin.getId()
        + ",\"amountMode\":\""
        + amountMode
        + "\",\"amountTaken\":"
        + amountTakenJson
        + (extraFieldsJson == null ? "" : ",\"extraFields\":" + extraFieldsJson)
        + "}";
  }

  /** The wizard's client-assembled body for a creating operation (buildOperationRequest.ts). */
  private static String oldShape(
      String operationType,
      String originsJson,
      String name,
      int count,
      String eachAmountJson,
      String totalJson,
      String extraFieldsJson,
      String storageTempJson) {
    StringBuilder subSamples = new StringBuilder();
    for (int i = 0; i < count; i++) {
      subSamples
          .append(i == 0 ? "" : ",")
          .append("{\"quantity\":")
          .append(eachAmountJson)
          .append(",\"extraFields\":[]}");
    }
    return "{\"operationType\":\""
        + operationType
        + "\",\"origins\":["
        + originsJson
        + "],\"newSample\":{\"name\":\""
        + name
        + "\",\"templateId\":null,\"quantity\":"
        + totalJson
        + ",\"extraFields\":["
        + extraFieldsJson
        + "],\"subSamples\":["
        + subSamples
        + "]"
        + (storageTempJson == null
            ? ""
            : ",\"storageTempMin\":" + storageTempJson + ",\"storageTempMax\":" + storageTempJson)
        + "}}";
  }

  private static String newShape(String operationType, String originsJson, String inputsJson) {
    return "{\"operationType\":\""
        + operationType
        + "\",\"origins\":["
        + originsJson
        + "],\"inputs\":"
        + inputsJson
        + "}";
  }
}
