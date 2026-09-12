package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
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
 * For each of the seven configured operations, the records the server persists from the request the
 * wizard sends, as a golden fingerprint: the created sample (name, derived total, template, storage
 * range, every extra field with its definition key and link), each of its subsamples, and each
 * origin's quantity and fields afterwards.
 *
 * <p>The fingerprints are the records the client-assembled shape persisted before the server
 * started building the sample (DevDocs/adr/0007 M3 proved the two shapes identical, M5 deleted the
 * client-assembled one). The wizard's confirmation preview is checked against the wizard's own
 * model of the build (OperationConfirmation.test), so this is the only thing tying that model to
 * what the server actually stores: a change here that is not a deliberate change to the records is
 * the preview drifting.
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
  public void aliquotPersistsTheGoldenRecords() throws Exception {
    assertPersists(
        1,
        o ->
            body(
                "aliquot",
                origin(o.get(0), "explicit", q("1", GRAM)),
                "{\"sampleName\":\"Aliquots\",\"count\":2,\"eachAmount\":" + q("0.5", GRAM) + "}"),
        "sample Aliquots total=1/7 template=null storage=none..none",
        "  field Derived from|link|null|operations.aliquot.linkFieldName|IsPartOf->ORIGIN0",
        "  subsample Aliquots.01 quantity=0.5/7 fields=0",
        "  subsample Aliquots.02 quantity=0.5/7 fields=0",
        "origin quantity=4/7");
  }

  @Test
  public void passagePersistsTheGoldenRecords() throws Exception {
    // The origin's parent carries no passage counter, so the server starts it at 1.
    assertPersists(
        1,
        o ->
            body(
                "passage",
                origin(o.get(0), "explicit", q("0", GRAM)),
                "{\"sampleName\":\"Passaged\",\"count\":1,\"eachAmount\":" + q("0.5", GRAM) + "}"),
        "sample Passaged total=0.5/7 template=null storage=none..none",
        "  field Passaged from|link|null|operations.passage.linkFieldName|IsDerivedFrom->ORIGIN0",
        "  field Passage number|text|1|operations.passage.numberField|",
        "  subsample Passaged.01 quantity=0.5/7 fields=0",
        "origin quantity=5/7");
  }

  @Test
  public void poolPersistsTheGoldenRecords() throws Exception {
    // Both origins are named mySubSample, so the server disambiguates the two link names by target.
    assertPersists(
        2,
        o ->
            body(
                "pool",
                origin(o.get(0), "explicit", q("1", GRAM))
                    + ","
                    + origin(o.get(1), "explicit", q("1", GRAM)),
                "{\"sampleName\":\"Pooled\",\"count\":1,\"eachAmount\":" + q("2", GRAM) + "}"),
        "sample Pooled total=2/7 template=null storage=none..none",
        "  field Pooled from: mySubSample (ORIGIN0)|link|null|operations.pool.linkFieldName"
            + "|HasPart->ORIGIN0",
        "  field Pooled from: mySubSample (ORIGIN1)|link|null|operations.pool.linkFieldName"
            + "|HasPart->ORIGIN1",
        "  subsample Pooled.01 quantity=2/7 fields=0",
        "origin quantity=4/7",
        "origin quantity=4/7");
  }

  @Test
  public void derivePersistsTheGoldenRecords() throws Exception {
    assertPersists(
        1,
        o ->
            body(
                "derive",
                origin(o.get(0), "explicit", q("0.6", GRAM)),
                "{\"processName\":\"PCR\",\"sampleName\":\"Derived\",\"count\":1,\"eachAmount\":"
                    + q("0.5", GRAM)
                    + "}"),
        "sample Derived total=0.5/7 template=null storage=none..none",
        "  field Is Derived From using process: PCR|link|null|operations.derive.linkFieldName"
            + "|IsDerivedFrom->ORIGIN0",
        "  subsample Derived.01 quantity=0.5/7 fields=0",
        "origin quantity=4.4/7");
  }

  @Test
  public void cryopreservePersistsTheGoldenRecords() throws Exception {
    assertPersists(
        1,
        o ->
            body(
                "cryopreserve",
                origin(o.get(0), "explicit", q("0.6", GRAM)),
                "{\"sampleName\":\"Frozen\",\"count\":1,\"eachAmount\":"
                    + q("0.5", GRAM)
                    + ",\"cryomedium\":\"DMSO 10%\",\"storageTemp\":"
                    + q("-80", CELSIUS)
                    + "}"),
        "sample Frozen total=0.5/7 template=null storage=-80/8..-80/8",
        "  field Frozen from|link|null|operations.cryopreserve.linkFieldName"
            + "|IsDerivedFrom->ORIGIN0",
        "  field Cryomedium|text|DMSO 10%|operations.cryopreserve.cryomediumField|",
        "  subsample Frozen.01 quantity=0.5/7 fields=0",
        "origin quantity=4.4/7");
  }

  @Test
  public void revivePersistsTheGoldenRecords() throws Exception {
    assertPersists(
        1,
        o ->
            body(
                "revive",
                origin(o.get(0), "explicit", q("0.6", GRAM)),
                "{\"sampleName\":\"Revived\",\"count\":1,\"eachAmount\":"
                    + q("0.5", GRAM)
                    + ",\"storageTemp\":"
                    + q("4", CELSIUS)
                    + "}"),
        "sample Revived total=0.5/7 template=null storage=4/8..4/8",
        "  field Revived from|link|null|operations.revive.linkFieldName|IsDerivedFrom->ORIGIN0",
        "  subsample Revived.01 quantity=0.5/7 fields=0",
        "origin quantity=4.4/7");
  }

  @Test
  public void destroyPersistsTheGoldenRecords() throws Exception {
    // The server resolves "today" in the session's timezone, which for an API-key session is the
    // server's, so this expected date and the stored one come from the same clock and zone.
    assertPersists(
        1,
        o -> body("destroy", origin(o.get(0), "all", q("5", GRAM)), "{}"),
        "origin quantity=0/7",
        "  field Disposed|text|" + LocalDate.now() + "|operations.destroy.disposedField|");
  }

  @Test
  public void templateAndDocumentationTargetTravelTopLevel() throws Exception {
    // The two wizard-level choices that are not inputs. The server builds the sample from the
    // template and adds the IsDocumentedBy link itself, named from the shared catalog.
    ApiSampleTemplatePost templatePost = new ApiSampleTemplatePost();
    templatePost.setName("inputs shape template");
    templatePost.setDefaultUnitId(GRAM);
    ApiSampleTemplate template =
        getFromJsonResponseBody(
            mockMvc
                .perform(
                    createBuilderForPostWithJSONBody(
                        apiKey, "/sampleTemplates", anyUser, templatePost))
                .andExpect(status().isCreated())
                .andReturn(),
            ApiSampleTemplate.class);
    String documentGlobalId =
        createBasicDocumentInRootFolderWithText(anyUser, "protocol").getGlobalIdentifier();
    ApiSubSample origin = origins(1).get(0);

    Long sampleId =
        perform(
            "{\"operationType\":\"derive\",\"origins\":["
                + origin(origin, "explicit", q("0.6", GRAM))
                + "],\"inputs\":{\"processName\":\"PCR\",\"sampleName\":\"Documented\",\"count\":1,"
                + "\"eachAmount\":"
                + q("0.5", GRAM)
                + "},\"templateId\":"
                + template.getId()
                + ",\"documentedByGlobalId\":\""
                + documentGlobalId
                + "\"}");

    ApiSample created = sampleApiMgr.getApiSampleById(sampleId, anyUser);
    assertEquals(template.getId(), created.getTemplateId());
    ApiExtraField documentation =
        created.getExtraFields().stream()
            .filter(
                field ->
                    field.getLink() != null
                        && "IsDocumentedBy".equals(field.getLink().getRelationType()))
            .findFirst()
            .orElseThrow(
                () ->
                    new AssertionError("no documentation link among " + created.getExtraFields()));
    assertEquals(documentGlobalId, documentation.getLink().getTargetGlobalId());
    assertEquals("Documented by", documentation.getName());
    assertEquals("operations.documentationLink", documentation.getOperationFieldKey());
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
                    body(
                        "aliquot",
                        origin(origin, "explicit", q("1", GRAM)),
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
   * Performs the body against fresh origins and asserts the persisted records (created sample, its
   * fields and subsamples, each origin's quantity and fields) are exactly the golden lines, once
   * origin global ids are normalised to ORIGIN0, ORIGIN1...
   */
  private void assertPersists(
      int originCount, Function<List<ApiSubSample>, String> body, String... goldenLines)
      throws Exception {
    List<ApiSubSample> origins = origins(originCount);
    String records = fingerprint(perform(body.apply(origins)), origins);
    assertEquals(String.join("\n", goldenLines) + "\n", records);
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

  /** One origin element, as the wizard sends it. */
  private static String origin(ApiSubSample origin, String amountMode, String amountTakenJson) {
    return "{\"id\":"
        + origin.getId()
        + ",\"amountMode\":\""
        + amountMode
        + "\",\"amountTaken\":"
        + amountTakenJson
        + "}";
  }

  private static String body(String operationType, String originsJson, String inputsJson) {
    return "{\"operationType\":\""
        + operationType
        + "\",\"origins\":["
        + originsJson
        + "],\"inputs\":"
        + inputsJson
        + "}";
  }
}
