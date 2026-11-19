package com.researchspace.webapp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.researchspace.dao.FieldFormDao;
import com.researchspace.model.EditStatus;
import com.researchspace.model.User;
import com.researchspace.model.dtos.ChoiceFieldDTO;
import com.researchspace.model.dtos.DateFieldDTO;
import com.researchspace.model.dtos.FieldDisplayRuleDTO;
import com.researchspace.model.dtos.FormFieldSaveItem;
import com.researchspace.model.dtos.FormSharingCommand;
import com.researchspace.model.dtos.NumberFieldDTO;
import com.researchspace.model.dtos.RadioFieldDTO;
import com.researchspace.model.dtos.StringFieldDTO;
import com.researchspace.model.field.ChoiceFieldForm;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.field.RadioFieldForm;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.RSForm;
import com.researchspace.service.impl.RecordEditorTracker;
import com.researchspace.session.SessionAttributeUtils;
import com.researchspace.session.UserSessionTracker;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.web.servlet.ModelAndView;

/** The form field editor's read and save endpoints, and the edit lock around Update. */
public class FormFieldEditorMVCIT extends MVCTestBase {

  private static final String FIELDS_URL = "/workspace/editor/form/ajax/fields";

  private @Autowired RSFormController formController;
  private @Autowired RecordEditorTracker recordTracker;
  private @Autowired FieldFormDao fieldFormDao;
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
    assertThat(fieldsOf(read)).hasSize(2);
    JsonNode gmo = fieldsOf(read).get(0);
    String gmoKey = gmo.get("fieldKey").asText();
    assertEquals("Radio", gmo.get("type").asText());
    assertFalse(gmo.get("canDelete").asBoolean(), "copied from the published version");
    assertEquals("GMO?", gmo.get("source").get("name").asText());
    assertEquals("IS_ANY_OF", fieldsOf(read).get(1).get("displayRule").get("operator").asText());

    // rename the source, change the rule and add a field, sending back what was read
    ArrayNode fields = (ArrayNode) fieldsOf(read).deepCopy();
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
    JsonNode saved = postFields(draft.getId(), fields, versionOf(read));
    assertTrue(saved.get("errorMsg").isNull(), saved.toString());
    assertThat(fieldsOf(saved)).hasSize(3);
    assertEquals("Is this a GMO?", fieldsOf(saved).get(0).get("source").get("name").asText());
    assertEquals("IS_NONE_OF", fieldsOf(saved).get(1).get("displayRule").get("operator").asText());
    assertEquals(newKey, fieldsOf(saved).get(2).get("fieldKey").asText());
    assertTrue(fieldsOf(saved).get(2).get("canDelete").asBoolean());

    // renames again and adds another field, but the source's own rule names itself
    ArrayNode rejected = (ArrayNode) fieldsOf(saved).deepCopy();
    ((ObjectNode) rejected.get(0).get("source")).put("name", "rejected name");
    ((ObjectNode) rejected.get(0))
        .set(
            "displayRule",
            mapper.valueToTree(new FieldDisplayRuleDTO(gmoKey, "HAS_VALUE", List.of())));
    rejected.add(
        mapper.valueToTree(
            new FormFieldSaveItem(
                null, "String", null, null, new StringFieldDTO<>("More", false, "no", ""))));
    JsonNode failure = postFields(draft.getId(), rejected, versionOf(saved));
    assertTrue(failure.get("data").isNull());
    assertThat(failure.get("errorMsg").get("errorMessages").toString())
        .contains("cannot depend on the field itself");

    JsonNode reread = getFields(draft.getId());
    assertEquals(fieldsOf(saved), fieldsOf(reread));
    assertEquals(versionOf(saved), versionOf(reread));
    assertEquals(user.getUsername(), recordTracker.isEditing(form).orElse(null));
    formController.unlockForm(draft.getId(), pageLockToken(), principal);
  }

  @Test
  public void aSaveFromAPageThatReadOlderFieldsIsRejectedAndChangesNothing() throws Exception {
    RSForm form = publishedForm();
    RSForm draft = openInEditor(form);
    // two tabs of the same user read the same fields
    JsonNode tabA = getFields(draft.getId());
    JsonNode tabB = getFields(draft.getId());
    assertEquals(versionOf(tabA), versionOf(tabB));

    ArrayNode fromA = (ArrayNode) fieldsOf(tabA).deepCopy();
    ((ObjectNode) fromA.get(1)).putNull("displayRule");
    fromA.add(
        mapper.valueToTree(
            new FormFieldSaveItem(
                null,
                "Number",
                UUID.randomUUID().toString(),
                null,
                new NumberFieldDTO<>("0", "10", "2", "5", FieldType.NUMBER, "Amount", false))));
    fromA.add(
        mapper.valueToTree(
            new FormFieldSaveItem(
                null,
                "Date",
                UUID.randomUUID().toString(),
                null,
                new DateFieldDTO<>(
                    "2026-01-02", "2026-01-01", "2026-12-31", "yyyy-MM-dd", "On", false))));
    JsonNode savedByA = postFields(draft.getId(), fromA, versionOf(tabA));
    assertTrue(savedByA.get("errorMsg").isNull(), savedByA.toString());
    assertNotEquals(versionOf(tabA), versionOf(savedByA));
    assertEquals(versionOf(savedByA), versionOf(getFields(draft.getId())));

    // the stale tab renames a field and sends the field list it read, without A's new fields
    ArrayNode fromB = (ArrayNode) fieldsOf(tabB).deepCopy();
    ((ObjectNode) fromB.get(0).get("source")).put("name", "stale rename");
    MvcResult stale =
        postFieldsExpecting(draft.getId(), fromB, versionOf(tabB), status().isConflict());

    JsonNode rejection = mapper.readTree(stale.getResponse().getContentAsString());
    assertTrue(rejection.get("data").isNull());
    assertThat(rejection.get("errorMsg").get("errorMessages").toString())
        .contains("changed elsewhere");
    JsonNode afterStale = getFields(draft.getId());
    assertEquals(fieldsOf(savedByA), fieldsOf(afterStale));

    // the tab that saved last keeps saving
    ArrayNode again = (ArrayNode) fieldsOf(savedByA).deepCopy();
    ((ObjectNode) again.get(0).get("source")).put("name", "Is this a GMO?");
    JsonNode savedAgain = postFields(draft.getId(), again, versionOf(savedByA));
    assertTrue(savedAgain.get("errorMsg").isNull(), savedAgain.toString());
    formController.unlockForm(draft.getId(), pageLockToken(), principal);
  }

  @Test
  public void publishFromAPageThatCheckedOlderFieldsIsRefusedAndPublishesNothing()
      throws Exception {
    RSForm form = formMgr.create(user);
    formMgr.saveFields(
        form.getId(),
        List.of(
            new FormFieldSaveItem(
                null, "String", null, null, new StringFieldDTO<>("Notes", false, "no", ""))),
        user);
    // tab A checks its fields and opens the access dialog; tab B then saves
    JsonNode checkedByA = getFields(form.getId());
    ArrayNode fromB = (ArrayNode) fieldsOf(checkedByA).deepCopy();
    ((ObjectNode) fromB.get(0).get("source")).put("name", "Saved by B");
    JsonNode savedByB = postFields(form.getId(), fromB, versionOf(checkedByA));
    assertTrue(savedByB.get("errorMsg").isNull(), savedByB.toString());
    assertNotEquals(versionOf(checkedByA), versionOf(savedByB));

    MvcResult refused =
        publishExpecting(form.getId(), versionOf(checkedByA), status().isConflict());

    assertThat(refused.getResponse().getContentAsString()).contains("changed elsewhere");
    assertFalse(doInTransaction(() -> formMgr.get(form.getId(), user).isPublishedAndVisible()));
    assertEquals(versionOf(savedByB), versionOf(getFields(form.getId())));
    publishExpecting(form.getId(), versionOf(savedByB), status().isOk());
    assertTrue(doInTransaction(() -> formMgr.get(form.getId(), user).isPublishedAndVisible()));
  }

  @Test
  public void updateFromAPageThatCheckedOlderFieldsIsRefusedAndChangesNothing() throws Exception {
    RSForm form = publishedForm();
    RSForm draft = openInEditor(form);
    String lockToken = pageLockToken();
    JsonNode checkedByA = getFields(draft.getId());
    ArrayNode fromB = (ArrayNode) fieldsOf(checkedByA).deepCopy();
    ((ObjectNode) fromB.get(0).get("source")).put("name", "Saved by B");
    JsonNode savedByB = postFields(draft.getId(), fromB, versionOf(checkedByA));

    MvcResult refused =
        updateExpecting(draft.getId(), lockToken, versionOf(checkedByA), status().isConflict());

    assertThat(refused.getResponse().getContentAsString()).contains("changed elsewhere");
    assertTrue(doInTransaction(() -> formMgr.get(draft.getId(), user).isTemporary()));
    assertTrue(doInTransaction(() -> formMgr.get(form.getId(), user).isCurrent()));
    assertEquals(user.getUsername(), recordTracker.isEditing(form).orElse(null));
    assertEquals(fieldsOf(savedByB), fieldsOf(getFields(draft.getId())));

    updateExpecting(draft.getId(), lockToken, versionOf(savedByB), status().isOk());
    assertFalse(doInTransaction(() -> formMgr.get(draft.getId(), user).isTemporary()));
  }

  @Test
  public void aChangeTheDatabaseRefusesAsConcurrentIsAConflictNotAServerError() {
    SQLException changedSinceRead =
        new SQLException("Record has changed since last read", "HY000", 1020);
    MockHttpServletResponse response = new MockHttpServletResponse();

    ModelAndView conflict =
        formController.handleExceptions(
            new MockHttpServletRequest(),
            response,
            new UncategorizedSQLException("save field", "update FieldForm", changedSinceRead));

    assertEquals(409, response.getStatus());
    assertEquals(ControllerExceptionHandler.AJAX_ERROR_VIEW_NAME, conflict.getViewName());
    assertThat(
            (String)
                conflict.getModel().get(ControllerExceptionHandler.EXCEPTION_MESSAGE_ATTR_NAME))
        .contains("changed by someone else");

    MockHttpServletResponse other = new MockHttpServletResponse();
    formController.handleExceptions(
        new MockHttpServletRequest(),
        other,
        new UncategorizedSQLException(
            "save field", "update FieldForm", new SQLException("x", "HY000", 1146)));
    assertNotEquals(409, other.getStatus());
  }

  @Test
  public void optionsStoredInAnOldFormatAreKeptAsTheyAreWhenSentBackUnchanged() throws Exception {
    RSForm form = formMgr.create(user);
    formMgr.saveFields(
        form.getId(),
        List.of(
            new FormFieldSaveItem(
                null,
                "Radio",
                null,
                null,
                new RadioFieldDTO<>("fieldChoices=X&fieldChoices=Y", "", "R", false, false)),
            new FormFieldSaveItem(
                null,
                "Choice",
                null,
                null,
                new ChoiceFieldDTO<>("fieldChoices=A&fieldChoices=B", "no", "", "C", false)),
            new FormFieldSaveItem(
                null, "String", null, null, new StringFieldDTO<>("S", false, "no", ""))),
        user);
    // as written long ago or through the forms API, which did not check options
    doInTransaction(
        () -> {
          List<FieldForm> fields = formMgr.get(form.getId(), user).getFieldForms();
          RadioFieldForm radio = (RadioFieldForm) fields.get(0);
          radio.setRadioOption("fieldChoices=X&&fieldChoices=Y=1");
          radio.setDefaultRadioOption("Y=1");
          ChoiceFieldForm choice = (ChoiceFieldForm) fields.get(1);
          choice.setChoiceOptions("fieldChoices=pH=7&fieldChoices=&fieldChoices=B");
          choice.setDefaultChoiceOption("fieldSelectedChoices=B");
          fields.forEach(fieldFormDao::save);
        });
    JsonNode read = getFields(form.getId());

    ArrayNode fields = (ArrayNode) fieldsOf(read).deepCopy();
    ((ObjectNode) fields.get(2).get("source")).put("name", "S2");
    JsonNode saved = postFields(form.getId(), fields, versionOf(read));

    assertTrue(saved.get("errorMsg").isNull(), saved.toString());
    JsonNode radio = fieldsOf(saved).get(0).get("source");
    assertEquals("fieldChoices=X&&fieldChoices=Y=1", radio.get("radioValues").asText());
    assertEquals("Y=1", radio.get("radioSelected").asText());
    JsonNode choice = fieldsOf(saved).get(1).get("source");
    assertEquals(
        "fieldChoices=pH=7&fieldChoices=&fieldChoices=B", choice.get("choiceValues").asText());
    assertEquals("fieldSelectedChoices=B", choice.get("selectedValues").asText());
  }

  @Test
  public void aReaderCanDeleteNoFieldAndASaveIsForbiddenAsJson() throws Exception {
    RSForm form = formMgr.create(user);
    formMgr.saveFields(
        form.getId(),
        List.of(
            new FormFieldSaveItem(
                null, "String", null, null, new StringFieldDTO<>("S", false, "no", ""))),
        user);
    FormSharingCommand readable = new FormSharingCommand();
    readable.setWorldOptions(List.of(PermissionType.READ.toString()));
    formMgr.updatePermissions(form.getId(), readable, user);
    JsonNode asOwner = getFields(form.getId());
    assertTrue(fieldsOf(asOwner).get(0).get("canDelete").asBoolean());

    User reader = createInitAndLoginAnyUser();
    principal = new MockPrincipal(reader.getUsername());
    JsonNode asReader = getFields(form.getId());
    assertFalse(fieldsOf(asReader).get(0).get("canDelete").asBoolean());

    ArrayNode fields = (ArrayNode) fieldsOf(asReader).deepCopy();
    ((ObjectNode) fields.get(0).get("source")).put("name", "changed by a reader");
    MvcResult forbidden =
        postFieldsExpecting(form.getId(), fields, versionOf(asReader), status().isForbidden());

    assertThat(forbidden.getResponse().getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);
    JsonNode body = mapper.readTree(forbidden.getResponse().getContentAsString());
    assertTrue(body.get("data").isNull());
    assertThat(body.get("errorMsg").get("errorMessages").toString())
        .contains("Unauthorised attempt to update a form field");
    assertEquals(fieldsOf(asReader), fieldsOf(getFields(form.getId())));
  }

  @Test
  public void updateReleasesTheEditLockSoAnotherUserCanEditAtOnce() throws Exception {
    RSForm form = publishedForm();
    RSForm draft = openInEditor(form);
    assertEquals(user.getUsername(), recordTracker.isEditing(form).orElse(null));

    Long newVersionId =
        formController
            .updateForm(draft.getId(), pageLockToken(), null, principal)
            .getBody()
            .getData();

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
  public void leavingTheEditorOnAFirstEditReleasesTheLockOnTheOriginal() throws Exception {
    RSForm form = publishedForm();
    RSForm draft = openInEditor(form);
    assertEquals(user.getUsername(), recordTracker.isEditing(form).orElse(null));

    // the page shows the draft but its token names the lock it took, on the original
    formController.unlockForm(draft.getId(), pageLockToken(), principal);

    assertTrue(recordTracker.isEditing(form).isEmpty());
    assertEquals(EditStatus.EDIT_MODE, anotherUserAttemptsToEdit(form));
  }

  @Test
  public void aStalePagesUnlockAfterAReloadKeepsTheCurrentPagesLock() throws Exception {
    RSForm form = publishedForm();
    RSForm draft = openInEditor(form);
    String stalePage = pageLockToken();
    // a reload, or a back-forward restore that reloads, opens the editor again in the same session
    formController.editForm(model, principal, form.getId());
    String currentPage = pageLockToken();
    assertNotEquals(stalePage, currentPage);

    // the stale page's unlock arrives late, and twice
    formController.unlockForm(draft.getId(), stalePage, principal);
    formController.unlockForm(draft.getId(), stalePage, principal);

    assertEquals(user.getUsername(), recordTracker.isEditing(form).orElse(null));
    assertEquals(EditStatus.CANNOT_EDIT_OTHER_EDITING, anotherUserAttemptsToEdit(form));
    formController.unlockForm(draft.getId(), currentPage, principal);
    assertTrue(recordTracker.isEditing(form).isEmpty());
  }

  @Test
  public void anotherUserCannotReleaseAPagesLockWithItsToken() throws Exception {
    RSForm form = publishedForm();
    RSForm draft = openInEditor(form);
    String token = pageLockToken();
    User other = createAndSaveUser(getRandomAlphabeticString("other"));

    formController.unlockForm(draft.getId(), token, new MockPrincipal(other.getUsername()));

    assertEquals(user.getUsername(), recordTracker.isEditing(form).orElse(null));
    formController.unlockForm(draft.getId(), token, principal);
    assertTrue(recordTracker.isEditing(form).isEmpty());
  }

  @Test
  public void theSameUserInAnotherSessionCannotReleaseAPagesLock() throws Exception {
    RSForm form = publishedForm();
    RSForm draft = openInEditor(form);
    String token = pageLockToken();
    Subject anotherSession = new Subject.Builder().buildSubject();
    anotherSession.login(new UsernamePasswordToken(user.getUsername(), TESTPASSWD));

    anotherSession.execute(() -> formController.unlockForm(draft.getId(), token, principal));
    anotherSession.logout();

    assertEquals(user.getUsername(), recordTracker.isEditing(form).orElse(null));
    // the token was not used up: it still releases the lock from the editing session
    formController.unlockForm(draft.getId(), token, principal);
    assertTrue(recordTracker.isEditing(form).isEmpty());
  }

  @Test
  public void savingInOnePageKeepsAnotherPagesLockInTheSameSession() throws Exception {
    RSForm form = publishedForm();
    RSForm draft = openInEditor(form);
    String firstEditPage = pageLockToken();
    formController.editForm(model, principal, draft.getId());
    String resumedDraftPage = pageLockToken();

    formController.saveForm(draft.getId(), firstEditPage, principal);
    formController.unlockForm(draft.getId(), firstEditPage, principal);

    assertTrue(recordTracker.isEditing(form).isEmpty());
    assertEquals(EditStatus.CANNOT_EDIT_OTHER_EDITING, anotherUserAttemptsToEdit(draft));
    formController.unlockForm(draft.getId(), resumedDraftPage, principal);
    assertTrue(recordTracker.isEditing(draft).isEmpty());
  }

  @Test
  public void updatingInOnePageKeepsAnotherPagesLockInTheSameSession() throws Exception {
    RSForm form = publishedForm();
    RSForm draft = openInEditor(form);
    String firstEditPage = pageLockToken();
    formController.editForm(model, principal, draft.getId());
    String resumedDraftPage = pageLockToken();

    formController.updateForm(draft.getId(), firstEditPage, null, principal);
    formController.unlockForm(draft.getId(), firstEditPage, principal);

    assertTrue(recordTracker.isEditing(form).isEmpty());
    assertEquals(EditStatus.CANNOT_EDIT_OTHER_EDITING, anotherUserAttemptsToEdit(draft));
    formController.unlockForm(draft.getId(), resumedDraftPage, principal);
    assertTrue(recordTracker.isEditing(draft).isEmpty());
  }

  @Test
  public void releasesWithoutATokenOnlyReleaseLocksTakenWithoutOne() throws Exception {
    RSForm form = publishedForm();
    RSForm draft = openInEditor(form);
    String page = pageLockToken();

    formController.unlockForm(form.getId(), null, principal);
    formController.unlockForm(form.getId(), null, principal);

    assertEquals(EditStatus.CANNOT_EDIT_OTHER_EDITING, anotherUserAttemptsToEdit(form));
    // a lock taken without a token is still released by id, and the page's lock stays
    formMgr.getForEditing(form.getId(), user, anySessionTracker());
    formController.unlockForm(form.getId(), null, principal);
    assertEquals(1, editCount(form));
    formController.unlockForm(draft.getId(), page, principal);
    assertTrue(recordTracker.isEditing(form).isEmpty());
  }

  @Test
  public void theEndOfASessionClearsItsPagesHolds() throws Exception {
    RSForm form = publishedForm();
    RSForm draft = openInEditor(form);
    String pageBeforeTheEnd = pageLockToken();

    formMgr.removeFromEditorTracker(SessionAttributeUtils.getSessionId());

    assertTrue(recordTracker.isEditing(form).isEmpty());
    // the old hold is gone, so its token cannot release a lock taken later in the session
    formController.editForm(model, principal, form.getId());
    String nextPage = pageLockToken();
    formController.unlockForm(draft.getId(), pageBeforeTheEnd, principal);
    assertEquals(EditStatus.CANNOT_EDIT_OTHER_EDITING, anotherUserAttemptsToEdit(form));
    formController.unlockForm(draft.getId(), nextPage, principal);
    assertTrue(recordTracker.isEditing(form).isEmpty());
  }

  @Test
  public void savingAFormOutsideASessionSavesItAndReleasesNoLock() throws Exception {
    RSForm form = publishedForm();
    RSForm draft = openInEditor(form);
    // as an archive import under /api/** or a startup task saves forms: no session
    Subject noSession =
        new Subject.Builder()
            .principals(SecurityUtils.getSubject().getPrincipals())
            .authenticated(true)
            .sessionCreationEnabled(false)
            .buildSubject();
    draft.setName("saved without a session");

    RSForm saved = noSession.execute(() -> formMgr.save(draft, user));

    assertEquals("saved without a session", saved.getName());
    assertEquals(EditStatus.CANNOT_EDIT_OTHER_EDITING, anotherUserAttemptsToEdit(form));
    formController.unlockForm(draft.getId(), pageLockToken(), principal);
  }

  @Test
  public void aReleaseByIdAsThePageGetsTheLockCannotTakeIt() throws Exception {
    RSForm form = publishedForm();
    Object manager = AopTestUtils.getUltimateTargetObject(formMgr);
    RecordEditorTracker racing = spy(recordTracker);
    Thread[] release = new Thread[1];
    doAnswer(
            acquiring -> {
              Object status = acquiring.callRealMethod();
              // right after the acquisition, a release by id from the same session
              release[0] =
                  new Thread(
                      SecurityUtils.getSubject()
                          .associateWith(() -> formMgr.unlockForm(form, user)));
              release[0].start();
              awaitDoneOrBlockedOnThisThread(release[0]);
              return status;
            })
        .when(racing)
        .attemptToEdit(any(), any(), any(), any());
    ReflectionTestUtils.setField(manager, "formTracker", racing);
    try {
      formController.editForm(model, principal, form.getId());
    } finally {
      ReflectionTestUtils.setField(manager, "formTracker", recordTracker);
    }
    release[0].join(10_000);

    assertEquals(1, editCount(form));
    assertEquals(EditStatus.CANNOT_EDIT_OTHER_EDITING, anotherUserAttemptsToEdit(form));
    formController.unlockForm(form.getId(), pageLockToken(), principal);
    assertTrue(recordTracker.isEditing(form).isEmpty());
  }

  /** Waits until a thread has finished, or is blocked on a monitor the current thread holds. */
  private static void awaitDoneOrBlockedOnThisThread(Thread thread) throws InterruptedException {
    long self = Thread.currentThread().getId();
    long deadline = System.currentTimeMillis() + 10_000;
    while (thread.getState() != Thread.State.TERMINATED) {
      ThreadInfo info = ManagementFactory.getThreadMXBean().getThreadInfo(thread.getId());
      if (info != null && info.getLockOwnerId() == self) {
        return;
      }
      if (System.currentTimeMillis() > deadline) {
        fail("the release neither finished nor waited for the editor page");
      }
      Thread.sleep(5);
    }
  }

  @Test
  public void theEndOfOneOfAUsersSessionsReleasesItsLocksAndHoldsOnEveryForm() throws Exception {
    RSForm form = publishedForm();
    RSForm draft = openInEditor(form);
    String formPage = pageLockToken();
    RSForm second = publishedForm();
    openInEditor(second);
    String secondPage = pageLockToken();
    // the same user has the first form open in another session too
    recordTracker.attemptToEdit(form.getId(), user, anySessionTracker(), () -> "another session");

    formMgr.removeFromEditorTracker(SessionAttributeUtils.getSessionId());

    assertEquals(0, editCount(form));
    assertEquals(
        1, recordTracker.getEditCount(form.getId(), user.getUsername(), "another session"));
    assertTrue(recordTracker.isEditing(second).isEmpty());
    assertFalse(formMgr.releaseEditLock(formPage, user));
    assertFalse(formMgr.releaseEditLock(secondPage, user));
    recordTracker.unlockRecord(form, user, () -> "another session");
    assertTrue(recordTracker.isEditing(draft).isEmpty());
  }

  @Test
  public void leavingAfterRevertingAResumedDraftReleasesItsLock() throws Exception {
    RSForm form = publishedForm();
    RSForm draft = openInEditor(form);
    formController.unlockForm(draft.getId(), pageLockToken(), principal);
    formController.editForm(model, principal, draft.getId());
    String resumedDraftPage = pageLockToken();

    formController.abandonUpdateForm(draft.getId(), principal);
    formController.unlockForm(draft.getId(), resumedDraftPage, principal);

    assertTrue(recordTracker.isEditing(draft).isEmpty());
    assertFalse(formMgr.releaseEditLock(resumedDraftPage, user));
  }

  private int editCount(RSForm form) {
    return recordTracker.getEditCount(
        form.getId(), user.getUsername(), SessionAttributeUtils.getSessionId());
  }

  /** What another user, in another session, gets on trying to edit the form. */
  private EditStatus anotherUserAttemptsToEdit(RSForm form) {
    User other = createAndSaveUser(getRandomAlphabeticString("other"));
    UserSessionTracker active = new UserSessionTracker();
    active.addUser(user.getUsername(), new MockHttpSession());
    EditStatus status =
        recordTracker.attemptToEdit(form.getId(), other, active, () -> "other session");
    recordTracker.unlockRecord(form, other, () -> "other session");
    return status;
  }

  /** The token the form editor page that last loaded was given with its lock. */
  private String pageLockToken() {
    String token = (String) model.asMap().get("editLockToken");
    assertNotNull(token);
    return token;
  }

  @Test
  public void updateOfAResumedDraftReleasesTheLockOnTheDraft() throws Exception {
    RSForm form = publishedForm();
    RSForm draft = openInEditor(form);
    formController.unlockForm(draft.getId(), pageLockToken(), principal);
    // resuming opens the draft's own id
    formController.editForm(model, principal, draft.getId());
    assertEquals(user.getUsername(), recordTracker.isEditing(draft).orElse(null));

    formController.updateForm(draft.getId(), pageLockToken(), null, principal);

    assertTrue(recordTracker.isEditing(draft).isEmpty());
  }

  @Test
  public void failedUpdateReportsAnErrorAndKeepsTheDraftAndTheEditLock() throws Exception {
    RSForm form = publishedForm();
    RSForm draft = openInEditor(form);
    JsonNode before = getFields(draft.getId());

    // the published form is not a draft, so there is no version to commit
    AjaxReturnObject<Long> response =
        formController.updateForm(form.getId(), null, null, principal).getBody();

    assertNull(response.getData());
    assertThat(response.getErrorMsg().getErrorMessages())
        .anySatisfy(message -> assertThat(message).contains("It may be a system form"));
    assertEquals(fieldsOf(before), fieldsOf(getFields(draft.getId())));
    assertTrue(doInTransaction(() -> formMgr.get(draft.getId(), user).isTemporary()));
    assertEquals(user.getUsername(), recordTracker.isEditing(form).orElse(null));
    formController.unlockForm(draft.getId(), pageLockToken(), principal);
  }

  @Test
  public void malformedRulesAndEmptyEntriesAreFieldErrors() throws Exception {
    RSForm form = publishedForm();
    RSForm draft = openInEditor(form);
    JsonNode read = getFields(draft.getId());
    String gmoKey = fieldsOf(read).get(0).get("fieldKey").asText();
    String prefix = "{\"sourceFieldKey\":\"" + gmoKey + "\",\"operator\":\"IS_ANY_OF\",";
    // a number where an option is expected, and a property the rule format does not have
    List<String> malformed =
        List.of(prefix + "\"values\":[1]}", prefix + "\"values\":[\"Yes\"],\"note\":\"x\"}");

    for (String rule : malformed) {
      ArrayNode fields = (ArrayNode) fieldsOf(read).deepCopy();
      ((ObjectNode) fields.get(1)).set("displayRule", mapper.readTree(rule));
      JsonNode failure = postFields(draft.getId(), fields, versionOf(read));
      assertTrue(failure.get("data").isNull());
      assertThat(failure.get("errorMsg").get("errorMessages").toString())
          .contains("display rule on field [Permit] is malformed");
    }
    ArrayNode withEmptyEntry = (ArrayNode) fieldsOf(read).deepCopy();
    withEmptyEntry.addNull();
    JsonNode failure = postFields(draft.getId(), withEmptyEntry, versionOf(read));
    assertTrue(failure.get("data").isNull());
    assertThat(failure.get("errorMsg").get("errorMessages").toString())
        .contains("Field #3 in the list is empty");

    assertEquals(fieldsOf(read), fieldsOf(getFields(draft.getId())));
    formController.unlockForm(draft.getId(), pageLockToken(), principal);
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

  private static JsonNode fieldsOf(JsonNode response) {
    return response.get("data").get("fields");
  }

  private static String versionOf(JsonNode response) {
    return response.get("data").get("version").asText();
  }

  private JsonNode postFields(Long formId, JsonNode fields, String version) throws Exception {
    return mapper.readTree(
        postFieldsExpecting(formId, fields, version, status().isOk())
            .getResponse()
            .getContentAsString());
  }

  private MvcResult postFieldsExpecting(
      Long formId, JsonNode fields, String version, ResultMatcher expected) throws Exception {
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
                    .param("version", version)
                    .principal(principal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(fields)))
            .andExpect(expected)
            .andReturn();
    return result;
  }

  /** Opens the access dialog, which keeps its command in the session, then confirms it. */
  private MvcResult publishExpecting(Long formId, String fieldsRevision, ResultMatcher expected)
      throws Exception {
    MockHttpSession session = new MockHttpSession();
    mockMvc
        .perform(
            get("/workspace/editor/form/ajax/publishAndShare")
                .param("templateId", formId.toString())
                .session(session)
                .principal(principal))
        .andExpect(status().isOk());
    return mockMvc
        .perform(
            post("/workspace/editor/form/ajax/publishAndShare")
                .session(session)
                .param("formId", formId.toString())
                .param("groupOptions", "READ")
                .param("worldOptions", "NONE")
                .param("fieldsRevision", fieldsRevision)
                .principal(principal))
        .andExpect(expected)
        .andReturn();
  }

  private MvcResult updateExpecting(
      Long formId, String lockToken, String fieldsRevision, ResultMatcher expected)
      throws Exception {
    return mockMvc
        .perform(
            post("/workspace/editor/form/ajax/updateForm")
                .param("templateId", formId.toString())
                .param("lockToken", lockToken)
                .param("fieldsRevision", fieldsRevision)
                .principal(principal))
        .andExpect(expected)
        .andReturn();
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
