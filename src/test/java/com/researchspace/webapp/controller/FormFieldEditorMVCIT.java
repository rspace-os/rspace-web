package com.researchspace.webapp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.researchspace.model.EditStatus;
import com.researchspace.model.User;
import com.researchspace.model.dtos.FieldDisplayRuleDTO;
import com.researchspace.model.dtos.FormFieldSaveItem;
import com.researchspace.model.dtos.RadioFieldDTO;
import com.researchspace.model.dtos.StringFieldDTO;
import com.researchspace.model.record.RSForm;
import com.researchspace.service.impl.RecordEditorTracker;
import com.researchspace.session.UserSessionTracker;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MvcResult;

/** The form field editor's read and save endpoints, and the edit lock around Update. */
public class FormFieldEditorMVCIT extends MVCTestBase {

  private static final String FIELDS_URL = "/workspace/editor/form/ajax/fields";

  private @Autowired RSFormController formController;
  private @Autowired RecordEditorTracker recordTracker;
  private final ObjectMapper mapper = new ObjectMapper();
  private User user;
  private MockPrincipal principal;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    MockServletContext servletContext = new MockServletContext();
    servletContext.setAttribute(UserSessionTracker.USERS_KEY, anySessionTracker());
    formController.setServletContext(servletContext);
    user = createInitAndLoginAnyUser();
    principal = new MockPrincipal(user.getUsername());
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void editorReadsAndSavesFieldsAndARejectedSaveChangesNothing() throws Exception {
    RSForm form = publishedForm();
    RSForm draft = openInEditor(form);

    JsonNode read = getFields(draft.getId());
    assertThat(read.get("data")).hasSize(2);
    JsonNode gmo = read.get("data").get(0);
    String gmoKey = gmo.get("fieldKey").asText();
    assertEquals("Radio", gmo.get("type").asText());
    assertFalse(gmo.get("canDelete").asBoolean(), "copied from the published version");
    assertEquals("GMO?", gmo.get("source").get("name").asText());
    assertEquals("IS_ANY_OF", read.get("data").get(1).get("displayRule").get("operator").asText());

    // rename the source, change the rule and add a field, sending back what was read
    ArrayNode fields = (ArrayNode) read.get("data").deepCopy();
    ((ObjectNode) fields.get(0).get("source")).put("name", "Is this a GMO?");
    ((ObjectNode) fields.get(0).get("source")).put("fieldName", "Is this a GMO?");
    ((ObjectNode) fields.get(1))
        .set(
            "displayRule",
            mapper.valueToTree(new FieldDisplayRuleDTO(gmoKey, "IS_NONE_OF", List.of("No"))));
    String newKey = UUID.randomUUID().toString();
    fields.add(
        mapper.valueToTree(
            new FormFieldSaveItem(
                null, "String", newKey, null, new StringFieldDTO<>("Notes", false, "no", ""))));
    JsonNode saved = postFields(draft.getId(), fields);
    assertTrue(saved.get("errorMsg").isNull(), saved.toString());
    assertThat(saved.get("data")).hasSize(3);
    assertEquals("Is this a GMO?", saved.get("data").get(0).get("source").get("name").asText());
    assertEquals(
        "IS_NONE_OF", saved.get("data").get(1).get("displayRule").get("operator").asText());
    assertEquals(newKey, saved.get("data").get(2).get("fieldKey").asText());
    assertTrue(saved.get("data").get(2).get("canDelete").asBoolean());

    // renames again and adds another field, but the source's own rule names itself
    ArrayNode rejected = (ArrayNode) saved.get("data").deepCopy();
    ((ObjectNode) rejected.get(0).get("source")).put("name", "rejected name");
    ((ObjectNode) rejected.get(0))
        .set(
            "displayRule",
            mapper.valueToTree(new FieldDisplayRuleDTO(gmoKey, "HAS_VALUE", List.of())));
    rejected.add(
        mapper.valueToTree(
            new FormFieldSaveItem(
                null, "String", null, null, new StringFieldDTO<>("More", false, "no", ""))));
    JsonNode failure = postFields(draft.getId(), rejected);
    assertTrue(failure.get("data").isNull());
    assertThat(failure.get("errorMsg").get("errorMessages").toString())
        .contains("cannot depend on the field itself");

    JsonNode reread = getFields(draft.getId());
    assertEquals(saved.get("data"), reread.get("data"));
    assertEquals(user.getUsername(), recordTracker.isEditing(form).orElse(null));
    formController.unlockForm(form.getId(), principal);
  }

  @Test
  public void updateReleasesTheEditLockSoAnotherUserCanEditAtOnce() throws Exception {
    RSForm form = publishedForm();
    RSForm draft = openInEditor(form);
    assertEquals(user.getUsername(), recordTracker.isEditing(form).orElse(null));

    Long newVersionId = formController.updateForm(draft.getId(), principal).getData();

    assertEquals(draft.getId(), newVersionId);
    assertTrue(recordTracker.isEditing(form).isEmpty());
    assertTrue(recordTracker.isEditing(draft).isEmpty());
    User other = createAndSaveUser(getRandomAlphabeticString("other"));
    UserSessionTracker active = new UserSessionTracker();
    active.addUser(user.getUsername(), new MockHttpSession());
    assertEquals(
        EditStatus.EDIT_MODE,
        recordTracker.attemptToEdit(newVersionId, other, active, () -> "other session"));
    recordTracker.unlockRecord(draft, other, () -> "other session");
  }

  @Test
  public void updateOfAResumedDraftReleasesTheLockOnTheDraft() throws Exception {
    RSForm form = publishedForm();
    RSForm draft = openInEditor(form);
    formController.unlockForm(form.getId(), principal);
    // resuming opens the draft's own id
    formController.editForm(model, principal, draft.getId());
    assertEquals(user.getUsername(), recordTracker.isEditing(draft).orElse(null));

    formController.updateForm(draft.getId(), principal);

    assertTrue(recordTracker.isEditing(draft).isEmpty());
  }

  @Test
  public void failedUpdateReportsAnErrorAndKeepsTheDraftAndTheEditLock() throws Exception {
    RSForm form = publishedForm();
    RSForm draft = openInEditor(form);
    JsonNode before = getFields(draft.getId());

    // the published form is not a draft, so there is no version to commit
    AjaxReturnObject<Long> response = formController.updateForm(form.getId(), principal);

    assertNull(response.getData());
    assertThat(response.getErrorMsg().getErrorMessages())
        .anySatisfy(message -> assertThat(message).contains("could not be made a new version"));
    assertEquals(before.get("data"), getFields(draft.getId()).get("data"));
    assertTrue(doInTransaction(() -> formMgr.get(draft.getId(), user).isTemporary()));
    assertEquals(user.getUsername(), recordTracker.isEditing(form).orElse(null));
    formController.unlockForm(form.getId(), principal);
  }

  @Test
  public void malformedRulesAndEmptyEntriesAreFieldErrors() throws Exception {
    RSForm form = publishedForm();
    RSForm draft = openInEditor(form);
    JsonNode read = getFields(draft.getId());
    String gmoKey = read.get("data").get(0).get("fieldKey").asText();
    String prefix = "{\"sourceFieldKey\":\"" + gmoKey + "\",\"operator\":\"IS_ANY_OF\",";
    // a number where an option is expected, and a property the rule format does not have
    List<String> malformed =
        List.of(prefix + "\"values\":[1]}", prefix + "\"values\":[\"Yes\"],\"note\":\"x\"}");

    for (String rule : malformed) {
      ArrayNode fields = (ArrayNode) read.get("data").deepCopy();
      ((ObjectNode) fields.get(1)).set("displayRule", mapper.readTree(rule));
      JsonNode failure = postFields(draft.getId(), fields);
      assertTrue(failure.get("data").isNull());
      assertThat(failure.get("errorMsg").get("errorMessages").toString())
          .contains("display rule on field [Permit] is malformed");
    }
    ArrayNode withEmptyEntry = (ArrayNode) read.get("data").deepCopy();
    withEmptyEntry.addNull();
    JsonNode failure = postFields(draft.getId(), withEmptyEntry);
    assertTrue(failure.get("data").isNull());
    assertThat(failure.get("errorMsg").get("errorMessages").toString())
        .contains("Field #3 in the list is empty");

    assertEquals(read.get("data"), getFields(draft.getId()).get("data"));
    formController.unlockForm(form.getId(), principal);
  }

  private RSForm openInEditor(RSForm form) throws Exception {
    formController.editForm(model, principal, form.getId());
    RSForm draft = (RSForm) model.asMap().get("template");
    assertTrue(draft.isTemporary());
    return draft;
  }

  private JsonNode getFields(Long formId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get(FIELDS_URL).param("formId", formId.toString()).principal(principal))
            .andExpect(status().isOk())
            .andReturn();
    return mapper.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode postFields(Long formId, JsonNode fields) throws Exception {
    // the read shape carries canDelete, which the save ignores
    for (JsonNode field : fields) {
      if (field instanceof ObjectNode object) {
        object.remove("canDelete");
      }
    }
    MvcResult result =
        mockMvc
            .perform(
                post(FIELDS_URL)
                    .param("formId", formId.toString())
                    .principal(principal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(fields)))
            .andExpect(status().isOk())
            .andReturn();
    return mapper.readTree(result.getResponse().getContentAsString());
  }

  /** A published form: radio "GMO?" (Yes, No) and string "Permit" shown when GMO? is Yes. */
  private RSForm publishedForm() throws Exception {
    String gmoKey = UUID.randomUUID().toString();
    RSForm form = formMgr.create(user);
    formMgr.saveFields(
        form.getId(),
        List.of(
            new FormFieldSaveItem(
                null,
                "Radio",
                gmoKey,
                null,
                new RadioFieldDTO<>("fieldChoices=Yes&fieldChoices=No", "", "GMO?", false, false)),
            new FormFieldSaveItem(
                null,
                "String",
                null,
                new FieldDisplayRuleDTO(gmoKey, "IS_ANY_OF", List.of("Yes")),
                new StringFieldDTO<>("Permit", false, "no", ""))),
        user);
    formMgr.publish(form.getId(), true, null, user);
    return doInTransaction(() -> formMgr.get(form.getId(), user));
  }
}
