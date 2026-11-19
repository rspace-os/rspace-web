package com.researchspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.controller.FormTemplatesCommon.FormFieldPost;
import com.researchspace.api.v1.controller.FormTemplatesCommon.RadioFieldPost;
import com.researchspace.api.v1.controller.FormTemplatesCommon.StringFieldPost;
import com.researchspace.model.User;
import com.researchspace.model.dtos.FieldDisplayRuleDTO;
import com.researchspace.model.dtos.FormFieldSaveItem;
import com.researchspace.model.dtos.RadioFieldDTO;
import com.researchspace.model.dtos.StringFieldDTO;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.record.FormType;
import com.researchspace.model.record.RSForm;
import com.researchspace.service.impl.RecordEditorTracker;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import liquibase.changelog.ChangeLogParameters;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Form field saves that need real commits: rollback of rejected saves, auditing, backfill. */
public class FormFieldsSaveIT extends RealTransactionSpringTestBase {

  private static final String YES_NO = "fieldChoices=Yes&fieldChoices=No";
  private static final String CHANGELOG = "sqlUpdates/changeLog-rsdev-1708.xml";
  private static final String BACKFILL_CHANGESET = "2026-11-18-rsdev1708-fieldform-key-backfill";
  private static final String DRAFT_KEY_CHANGESET =
      "2026-11-19-rsdev1708-draft-field-key-from-original";

  private @Autowired RecordEditorTracker recordTracker;
  private User user;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    user = createInitAndLoginAnyUser();
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void rejectedApiCreateLeavesNoForm() throws Exception {
    Long formsBefore = getCountOfEntityTable("RSForm");
    Long fieldsBefore = getCountOfEntityTable("FieldForm");
    StringFieldPost dependent = stringPost("Permit");
    dependent.setDisplayRule(showWhen(UUID.randomUUID().toString(), "Yes"));

    assertThrows(
        FormFieldsValidationException.class,
        () -> formMgr.createForm(definition(radioPost("GMO?"), dependent), user));

    assertEquals(formsBefore, getCountOfEntityTable("RSForm"));
    assertEquals(fieldsBefore, getCountOfEntityTable("FieldForm"));
  }

  @Test
  public void rejectedApiEditLeavesFieldsDraftAndEditLockAsTheyWere() throws Exception {
    RSForm form = publishedForm();
    Long formId = form.getId();
    List<FieldForm> originals = activeFields(form);
    // the user has the form open in the editor, with a draft
    RSForm draft = formMgr.getForEditing(formId, user, anySessionTracker());
    List<Long> draftFieldIds = activeFields(draft).stream().map(FieldForm::getId).toList();

    // renames both fields, but the rule names an option the source does not offer
    RadioFieldPost gmo = radioPost("GMO renamed");
    gmo.setId(originals.get(0).getId());
    StringFieldPost permit = stringPost("Permit renamed");
    permit.setId(originals.get(1).getId());
    permit.setDisplayRule(showWhen(originals.get(0).getFieldKey(), "Maybe"));
    assertThrows(
        FormFieldsValidationException.class,
        () -> formMgr.editForm(formId, definition(gmo, permit), user, anySessionTracker()));

    doInTransaction(
        () -> {
          RSForm reloaded = formMgr.get(formId, user);
          assertTrue(reloaded.isCurrent());
          assertThat(activeFields(reloaded))
              .extracting(FieldForm::getName)
              .containsExactly("GMO?", "Permit");
          RSForm reloadedDraft = reloaded.getTempForm();
          assertNotNull(reloadedDraft);
          assertEquals(draft.getId(), reloadedDraft.getId());
          assertThat(activeFields(reloadedDraft))
              .extracting(FieldForm::getId)
              .containsExactlyElementsOf(draftFieldIds);
          assertThat(activeFields(reloadedDraft))
              .extracting(FieldForm::getName)
              .containsExactly("GMO?", "Permit");
          assertThat(activeFields(reloadedDraft))
              .extracting(FieldForm::getDisplayRule)
              .containsExactly(null, rule(originals.get(0).getFieldKey(), "Yes"));
        });
    assertEquals(user.getUsername(), recordTracker.isEditing(form).orElse(null));
    formMgr.unlockForm(formId, user);
  }

  @Test
  public void apiEditThatCannotMakeANewVersionLeavesTheSavedDraftAndEditLockAsTheyWere()
      throws Exception {
    RSForm form = publishedForm();
    Long formId = form.getId();
    List<FieldForm> originals = activeFields(form);
    String gmoKey = originals.get(0).getFieldKey();
    // the user has saved, but not Updated, a draft with a field that only the draft has
    RSForm draft = formMgr.getForEditing(formId, user, anySessionTracker());
    List<FieldForm> draftFields = activeFields(draft);
    formMgr.saveFields(
        draft.getId(),
        List.of(
            new FormFieldSaveItem(draftFields.get(0).getId(), "Radio", null, null, radio("GMO?")),
            new FormFieldSaveItem(
                draftFields.get(1).getId(),
                "String",
                null,
                showWhen(gmoKey, "Yes"),
                string("Permit")),
            new FormFieldSaveItem(null, "String", null, null, string("draft only"))),
        user);
    // versioning refuses system forms
    doInTransaction(() -> formMgr.get(formId, user).setSystemForm(true));
    try {
      RadioFieldPost gmo = radioPost("GMO renamed");
      gmo.setId(originals.get(0).getId());
      StringFieldPost permit = stringPost("Permit renamed");
      permit.setId(originals.get(1).getId());

      assertThrows(
          FormVersionUpdateException.class,
          () -> formMgr.editForm(formId, definition(gmo, permit), user, anySessionTracker()));

      doInTransaction(
          () -> {
            RSForm reloaded = formMgr.get(formId, user);
            assertTrue(reloaded.isCurrent());
            assertThat(activeFields(reloaded))
                .extracting(FieldForm::getName)
                .containsExactly("GMO?", "Permit");
            RSForm reloadedDraft = reloaded.getTempForm();
            assertEquals(draft.getId(), reloadedDraft.getId());
            assertThat(activeFields(reloadedDraft))
                .extracting(FieldForm::getName)
                .containsExactly("GMO?", "Permit", "draft only");
            assertThat(activeFields(reloadedDraft))
                .extracting(FieldForm::getDisplayRule)
                .containsExactly(null, rule(gmoKey, "Yes"), null);
          });
      assertEquals(user.getUsername(), recordTracker.isEditing(form).orElse(null));
    } finally {
      doInTransaction(() -> formMgr.get(formId, user).setSystemForm(false));
      formMgr.unlockForm(formId, user);
    }
  }

  @Test
  public void rejectedApiEditOfAFormWithoutDraftCreatesNoDraft() throws Exception {
    RSForm form = publishedForm();
    List<FieldForm> originals = activeFields(form);
    StringFieldPost permit = stringPost("Permit");
    permit.setId(originals.get(1).getId());
    permit.setDisplayRule(showWhen(originals.get(1).getFieldKey(), "Yes"));
    RadioFieldPost gmo = radioPost("GMO?");
    gmo.setId(originals.get(0).getId());

    assertThrows(
        FormFieldsValidationException.class,
        () -> formMgr.editForm(form.getId(), definition(gmo, permit), user, anySessionTracker()));

    doInTransaction(() -> assertNull(formMgr.get(form.getId(), user).getTempForm()));
  }

  @Test
  public void rejectedEditorSaveLeavesPersistedFieldsUnchanged() throws Exception {
    RSForm form = publishedForm();
    RSForm draft = formMgr.getForEditing(form.getId(), user, anySessionTracker());
    List<FieldForm> draftFields = activeFields(draft);
    String gmoKey = draftFields.get(0).getFieldKey();
    // renames and reorders both fields and adds one, then fails on the whole-form rule check
    List<FormFieldSaveItem> fields =
        List.of(
            new FormFieldSaveItem(
                draftFields.get(1).getId(), "String", null, null, string("Permit renamed")),
            new FormFieldSaveItem(
                draftFields.get(0).getId(),
                "Radio",
                null,
                showWhen(gmoKey, "Yes"),
                radio("GMO renamed")),
            new FormFieldSaveItem(null, "String", null, null, string("added")));

    assertThrows(
        FormFieldsValidationException.class, () -> formMgr.saveFields(draft.getId(), fields, user));

    doInTransaction(
        () -> {
          RSForm reloaded = formMgr.get(draft.getId(), user);
          assertThat(activeFields(reloaded))
              .extracting(FieldForm::getName)
              .containsExactly("GMO?", "Permit");
          assertThat(activeFields(reloaded))
              .extracting(FieldForm::getDisplayRule)
              .containsExactly(null, rule(gmoKey, "Yes"));
        });
    assertEquals(user.getUsername(), recordTracker.isEditing(form).orElse(null));
    formMgr.unlockForm(form.getId(), user);
  }

  @Test
  public void keysAndRulesArePersistedAndAudited() throws Exception {
    RSForm form = publishedForm();
    List<FieldForm> fields = activeFields(form);
    Long sourceId = fields.get(0).getId();
    Long dependentId = fields.get(1).getId();
    String sourceKey = fields.get(0).getFieldKey();

    doInTransaction(
        () -> {
          FieldForm source = formMgr.getField(sourceId);
          FieldForm dependent = formMgr.getField(dependentId);
          assertThat(source.getFieldKey()).matches("[0-9a-f-]{36}");
          assertNull(source.getDisplayRule());
          assertEquals(rule(sourceKey, "Yes"), dependent.getDisplayRule());

          AuditReader audit = AuditReaderFactory.get(sessionFactory.getCurrentSession());
          for (FieldForm field : List.of(source, dependent)) {
            List<Number> revisions = audit.getRevisions(FieldForm.class, field.getId());
            FieldForm audited =
                audit.find(FieldForm.class, field.getId(), revisions.get(revisions.size() - 1));
            assertEquals(field.getFieldKey(), audited.getFieldKey());
            assertEquals(field.getDisplayRule(), audited.getDisplayRule());
          }
        });
  }

  @Test
  public void firstRuleOnABackfilledFieldIsAccepted() throws Exception {
    RSForm form = formMgr.create(user);
    form =
        formMgr.saveFields(
            form.getId(),
            List.of(
                new FormFieldSaveItem(null, "Radio", null, null, radio("GMO?")),
                new FormFieldSaveItem(null, "String", null, null, string("Permit"))),
            user);
    Long formId = form.getId();
    List<Long> ids = activeFields(form).stream().map(FieldForm::getId).toList();
    // as before the upgrade, then the changelog's backfill
    new JdbcTemplate(dataSource)
        .update("update FieldForm set fieldKey = null where form_id = ?", formId);
    runChangeSet(BACKFILL_CHANGESET);
    sessionFactory.getCache().evictEntityData(FieldForm.class);
    String backfilledKey = doInTransaction(() -> formMgr.getField(ids.get(0)).getFieldKey());
    assertNotNull(backfilledKey);

    formMgr.saveFields(
        formId,
        List.of(
            new FormFieldSaveItem(ids.get(0), "Radio", null, null, radio("GMO?")),
            new FormFieldSaveItem(
                ids.get(1), "String", null, showWhen(backfilledKey, "Yes"), string("Permit"))),
        user);

    assertEquals(
        rule(backfilledKey, "Yes"),
        doInTransaction(() -> formMgr.getField(ids.get(1)).getDisplayRule()));
  }

  @Test
  public void upgradeGivesUnsavedDraftCopiesTheKeysOfTheirOriginals() throws Exception {
    RSForm form = formMgr.create(user);
    formMgr.saveFields(
        form.getId(),
        List.of(
            new FormFieldSaveItem(null, "Radio", null, null, radio("GMO?")),
            new FormFieldSaveItem(null, "String", null, null, string("Permit"))),
        user);
    formMgr.publish(form.getId(), true, null, user);
    Long formId = form.getId();
    RSForm draft = formMgr.getForEditing(formId, user, anySessionTracker());
    Long draftId = draft.getId();
    // as before the upgrade: no field has a key
    new JdbcTemplate(dataSource)
        .update("update FieldForm set fieldKey = null where form_id in (?, ?)", formId, draftId);

    runChangeSet(BACKFILL_CHANGESET);
    List<String> originalKeys = fieldKeys(formId);
    assertThat(originalKeys).hasSize(2).doesNotContainNull();
    assertThat(fieldKeys(draftId)).doesNotContainAnyElementsOf(originalKeys);
    // re-running keeps the keys already given
    runChangeSet(BACKFILL_CHANGESET);
    assertEquals(originalKeys, fieldKeys(formId));

    runChangeSet(DRAFT_KEY_CHANGESET);
    assertEquals(originalKeys, fieldKeys(draftId));
    // re-running changes nothing
    runChangeSet(DRAFT_KEY_CHANGESET);
    assertEquals(originalKeys, fieldKeys(formId));
    assertEquals(originalKeys, fieldKeys(draftId));

    // an API client echoes the keys it read from the form
    sessionFactory.getCache().evictEntityData(FieldForm.class);
    List<FieldForm> originals = doInTransaction(() -> activeFields(formMgr.get(formId, user)));
    RadioFieldPost gmo = radioPost("GMO?");
    gmo.setId(originals.get(0).getId());
    gmo.setFieldKey(originalKeys.get(0));
    StringFieldPost permit = stringPost("Permit");
    permit.setId(originals.get(1).getId());
    permit.setFieldKey(originalKeys.get(1));
    permit.setDisplayRule(showWhen(originalKeys.get(0), "Yes"));
    formMgr.editForm(formId, definition(gmo, permit), user, anySessionTracker());
  }

  private void runChangeSet(String id) throws Exception {
    try (Connection connection = dataSource.getConnection();
        var resources = new ClassLoaderResourceAccessor()) {
      var changelog =
          ChangeLogParserFactory.getInstance()
              .getParser(CHANGELOG, resources)
              .parse(CHANGELOG, new ChangeLogParameters(), resources);
      var database =
          DatabaseFactory.getInstance()
              .findCorrectDatabaseImplementation(new JdbcConnection(connection));
      changelog.getChangeSets().stream()
          .filter(changeSet -> changeSet.getId().equals(id))
          .findFirst()
          .orElseThrow()
          .execute(changelog, database);
    }
  }

  /** The keys of the form's active fields, in field order. */
  private List<String> fieldKeys(Long formId) {
    return new JdbcTemplate(dataSource)
        .queryForList(
            "select fieldKey from FieldForm where form_id = ? and deleted = 0 order by columnIndex",
            String.class,
            formId);
  }

  /** A published form: radio "GMO?" (Yes, No) and string "Permit" shown when GMO? is Yes. */
  private RSForm publishedForm() throws Exception {
    String gmoKey = UUID.randomUUID().toString();
    RSForm form = formMgr.create(user);
    formMgr.saveFields(
        form.getId(),
        List.of(
            new FormFieldSaveItem(null, "Radio", gmoKey, null, radio("GMO?")),
            new FormFieldSaveItem(null, "String", null, showWhen(gmoKey, "Yes"), string("Permit"))),
        user);
    formMgr.publish(form.getId(), true, null, user);
    return doInTransaction(
        () -> {
          RSForm loaded = formMgr.get(form.getId(), user);
          loaded.getFieldForms().size();
          return loaded;
        });
  }

  private static String rule(String key, String value) {
    return "{\"sourceFieldKey\":\""
        + key
        + "\",\"operator\":\"IS_ANY_OF\",\"values\":[\""
        + value
        + "\"]}";
  }

  private static List<FieldForm> activeFields(RSForm form) {
    return form.getFieldForms().stream().filter(f -> !f.isDeleted()).toList();
  }

  private static FieldDisplayRuleDTO showWhen(String key, String value) {
    return new FieldDisplayRuleDTO(key, "IS_ANY_OF", List.of(value));
  }

  private static RadioFieldDTO<?> radio(String name) {
    return new RadioFieldDTO<>(YES_NO, "", name, false, false);
  }

  private static StringFieldDTO<?> string(String name) {
    return new StringFieldDTO<>(name, false, "no", "");
  }

  private static RadioFieldPost radioPost(String name) {
    RadioFieldPost radio = new RadioFieldPost();
    radio.setName(name);
    radio.setOptions(List.of("Yes", "No"));
    return radio;
  }

  private static StringFieldPost stringPost(String name) {
    StringFieldPost string = new StringFieldPost();
    string.setName(name);
    return string;
  }

  private static FormDefinitionCommand definition(FormFieldPost<?>... fields) {
    List<FormFieldSaveItem> items = new ArrayList<>();
    for (FormFieldPost<?> field : fields) {
      items.add(
          new FormFieldSaveItem(
              field.getId(),
              field.getType(),
              field.getFieldKey(),
              field.getDisplayRule(),
              field,
              !field.isDisplayRuleSet()));
    }
    return new FormDefinitionCommand("api form", null, FormType.NORMAL, items);
  }
}
