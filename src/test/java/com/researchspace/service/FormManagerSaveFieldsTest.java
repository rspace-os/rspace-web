package com.researchspace.service;

import static com.researchspace.testutils.RSpaceTestUtils.logout;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.controller.FormTemplatesCommon.AttachmentFieldPost;
import com.researchspace.api.v1.controller.FormTemplatesCommon.ChoiceFieldPost;
import com.researchspace.api.v1.controller.FormTemplatesCommon.FormFieldPost;
import com.researchspace.api.v1.controller.FormTemplatesCommon.NumberFieldPost;
import com.researchspace.api.v1.controller.FormTemplatesCommon.RadioFieldPost;
import com.researchspace.api.v1.controller.FormTemplatesCommon.ReferenceFieldPost;
import com.researchspace.api.v1.controller.FormTemplatesCommon.StringFieldPost;
import com.researchspace.api.v1.controller.FormTemplatesCommon.UriFieldPost;
import com.researchspace.model.User;
import com.researchspace.model.dtos.AbstractFormFieldDTO;
import com.researchspace.model.dtos.AttachmentFieldDTO;
import com.researchspace.model.dtos.ChoiceFieldDTO;
import com.researchspace.model.dtos.DateFieldDTO;
import com.researchspace.model.dtos.FieldDisplayRuleDTO;
import com.researchspace.model.dtos.FormFieldEditorItem;
import com.researchspace.model.dtos.FormFieldSaveItem;
import com.researchspace.model.dtos.LegacyFormFieldDTOs;
import com.researchspace.model.dtos.NumberFieldDTO;
import com.researchspace.model.dtos.RadioFieldDTO;
import com.researchspace.model.dtos.ReferenceFieldDTO;
import com.researchspace.model.dtos.StringFieldDTO;
import com.researchspace.model.dtos.TextFieldDTO;
import com.researchspace.model.dtos.TimeFieldDTO;
import com.researchspace.model.dtos.UriFieldDTO;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.field.RadioFieldForm;
import com.researchspace.model.field.StringFieldForm;
import com.researchspace.model.field.rule.ConditionOperator;
import com.researchspace.model.field.rule.FieldDisplayRule;
import com.researchspace.model.record.CopyIndependentFormAndFieldFormPolicy;
import com.researchspace.model.record.FormType;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.impl.RecordEditorTracker;
import com.researchspace.session.UserSessionTracker;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectRetrievalFailureException;

public class FormManagerSaveFieldsTest extends SpringTransactionalTest {

  private static final String YES_NO = "fieldChoices=Yes&fieldChoices=No";

  private final ObjectMapper mapper = new ObjectMapper();
  private @Autowired RecordEditorTracker recordTracker;
  private User user;

  @BeforeEach
  public void setUp() throws Exception {
    user = createAndSaveUserIfNotExists(getRandomAlphabeticString("saveFlds"));
    initialiseContentWithEmptyContent(user);
    logoutAndLoginAs(user);
  }

  @AfterEach
  public void tearDown() throws Exception {
    logout();
  }

  @Test
  public void newFieldsCanReferToEachOtherInOneSave() {
    RSForm form = formMgr.create(user);
    String gmoKey = newKey();
    // the dependent field is listed before its source
    List<FormFieldSaveItem> fields =
        List.of(
            item(null, null, showWhen(gmoKey, "Yes"), string("Permit number")),
            item(null, gmoKey, null, radio("Is this a GMO?", YES_NO)));

    RSForm saved = formMgr.saveFields(form.getId(), fields, user);

    List<FieldForm> active = activeFields(saved);
    assertThat(active)
        .extracting(FieldForm::getName)
        .containsExactly("Permit number", "Is this a GMO?");
    assertEquals(gmoKey, active.get(1).getFieldKey());
    assertNotNull(active.get(0).getFieldKey());
    assertEquals(
        new FieldDisplayRule(gmoKey, ConditionOperator.IS_ANY_OF, List.of("Yes")).toJson(),
        active.get(0).getDisplayRule());
    assertNull(active.get(1).getDisplayRule());
  }

  @Test
  public void saveUpdatesReordersAndDeletesOmittedFields() {
    RSForm form = formWith(string("one"), string("two"), string("three"));
    List<FormFieldSaveItem> current = current(form);
    ((StringFieldDTO<?>) current.get(2).getSource()).setName("three renamed");

    RSForm saved = formMgr.saveFields(form.getId(), List.of(current.get(2), current.get(0)), user);

    List<FieldForm> active = activeFields(saved);
    assertThat(active).extracting(FieldForm::getName).containsExactly("three renamed", "one");
    assertThat(active).extracting(FieldForm::getColumnIndex).containsExactly(0, 1);
    FieldForm two = formMgr.getField(current.get(1).getId());
    assertTrue(two.isDeleted());
  }

  @Test
  public void openingChangingOnlyARuleAndSavingKeepsEveryOtherSetting() throws Exception {
    RSForm form = formWith(fullyConfiguredFields());
    flushDatabaseState();
    List<FormFieldSaveItem> before = current(form);
    String radioKey = before.get(3).getFieldKey();
    List<Long> modifiedBefore =
        activeFields(form).stream().map(FieldForm::getModificationDate).toList();

    List<FormFieldSaveItem> edited = current(form);
    edited.get(1).setDisplayRule(showWhen(radioKey, "Yes"));
    formMgr.saveFields(form.getId(), edited, user);
    flushDatabaseState();
    sessionFactory.getCurrentSession().clear();

    RSForm reopened = formMgr.getWithPopulatedFieldForms(form.getId(), user);
    List<FormFieldSaveItem> after = current(reopened);
    assertEquals(before.size(), after.size());
    List<FieldForm> reopenedFields = activeFields(reopened);
    for (int i = 0; i < before.size(); i++) {
      assertEquals(before.get(i).getId(), after.get(i).getId());
      assertEquals(before.get(i).getType(), after.get(i).getType());
      assertEquals(before.get(i).getFieldKey(), after.get(i).getFieldKey());
      assertEquals(
          mapper.valueToTree(before.get(i).getSource()),
          mapper.valueToTree(after.get(i).getSource()),
          "settings of " + before.get(i).getType());
      if (i == 1) {
        assertEquals(showWhen(radioKey, "Yes"), after.get(i).getDisplayRule());
      } else {
        assertNull(after.get(i).getDisplayRule());
        assertEquals(modifiedBefore.get(i), reopenedFields.get(i).getModificationDate());
      }
    }
  }

  @Test
  public void uriReferenceAndAttachmentFieldsSurviveAnEditorSave() {
    RSForm form =
        formMgr.createForm(
            definition(
                apiField(new StringFieldPost(), "text"),
                apiField(new UriFieldPost(), "link"),
                apiField(new ReferenceFieldPost(), "reference"),
                apiField(new AttachmentFieldPost(), "attachment")),
            user);
    List<FormFieldSaveItem> current = current(form);
    assertThat(current)
        .extracting(FormFieldSaveItem::getSource)
        .hasExactlyElementsOfTypes(
            StringFieldDTO.class,
            UriFieldDTO.class,
            ReferenceFieldDTO.class,
            AttachmentFieldDTO.class);
    ((UriFieldDTO) current.get(1).getSource()).setName("renamed link");
    ((AttachmentFieldDTO) current.get(3).getSource()).setMandatory(true);

    RSForm saved = formMgr.saveFields(form.getId(), current, user);

    List<FieldForm> active = activeFields(saved);
    assertThat(active)
        .extracting(FieldForm::getType)
        .containsExactly(
            FieldType.STRING, FieldType.URI, FieldType.REFERENCE, FieldType.ATTACHMENT);
    assertEquals("renamed link", active.get(1).getName());
    assertTrue(active.get(3).isMandatory());
  }

  @Test
  public void savedFieldTypeCannotChange() {
    RSForm form = formWith(string("one"));
    FormFieldSaveItem asNumber = item(current(form).get(0).getId(), null, null, number("one"));

    FormFieldsValidationException e =
        assertThrows(
            FormFieldsValidationException.class,
            () -> formMgr.saveFields(form.getId(), List.of(asNumber), user));
    assertThat(e.getErrors()).anySatisfy(msg -> assertThat(msg).contains("cannot change its type"));
  }

  @Test
  public void unknownAndRepeatedIdsAreRejected() {
    RSForm form = formWith(string("one"));
    RSForm other = formWith(string("other"));
    FormFieldSaveItem foreign = current(other).get(0);
    assertThrows(
        FormFieldsValidationException.class,
        () -> formMgr.saveFields(form.getId(), List.of(foreign), user));

    FormFieldSaveItem one = current(form).get(0);
    FormFieldsValidationException e =
        assertThrows(
            FormFieldsValidationException.class,
            () -> formMgr.saveFields(form.getId(), List.of(one, one), user));
    assertThat(e.getErrors()).anySatisfy(msg -> assertThat(msg).contains("more than once"));
  }

  @Test
  public void keysAreImmutableAndUniqueIncludingDeletedFields() {
    RSForm form = formWith(string("one"), string("two"));
    List<FormFieldSaveItem> current = current(form);

    FormFieldSaveItem changedKey = current.get(0);
    changedKey.setFieldKey(newKey());
    assertThrows(
        FormFieldsValidationException.class,
        () -> formMgr.saveFields(form.getId(), List.of(changedKey, current.get(1)), user));

    List<FormFieldSaveItem> fresh = current(form);
    String deletedKey = fresh.get(1).getFieldKey();
    formMgr.saveFields(form.getId(), List.of(fresh.get(0)), user);
    FormFieldSaveItem reusesDeletedKey = item(null, deletedKey, null, string("three"));
    FormFieldsValidationException e =
        assertThrows(
            FormFieldsValidationException.class,
            () ->
                formMgr.saveFields(
                    form.getId(), List.of(current(form).get(0), reusesDeletedKey), user));
    assertThat(e.getErrors()).anySatisfy(msg -> assertThat(msg).contains("already uses"));

    FormFieldSaveItem malformedKey = item(null, "NOT-A-UUID", null, string("four"));
    assertThrows(
        FormFieldsValidationException.class,
        () -> formMgr.saveFields(form.getId(), List.of(current(form).get(0), malformedKey), user));
  }

  @Test
  public void invalidRulesRejectTheWholeSave() {
    RSForm form = formMgr.create(user);
    String a = newKey();
    String b = newKey();
    String password = newKey();
    FormFieldSaveItem passwordField =
        item(null, password, null, new StringFieldDTO<>("password", false, "yes", ""));

    // a cycle
    assertRejected(
        form,
        "loop",
        item(null, a, showWhen(b, "Yes"), radio("a", YES_NO)),
        item(null, b, showWhen(a, "Yes"), radio("b", YES_NO)));
    // an option the source does not offer
    assertRejected(
        form,
        "does not offer",
        item(null, a, null, radio("a", YES_NO)),
        item(null, null, showWhen(a, "Maybe"), string("dependent")));
    // a password field, whose value would appear in rule summaries
    assertRejected(
        form,
        "not a password field",
        passwordField,
        item(null, null, hasValue(password), string("dependent")));
    // a missing source
    assertRejected(
        form, "does not exist", item(null, null, showWhen(newKey(), "Yes"), string("dependent")));
    // an unknown operator
    assertRejected(
        form,
        "unknown operator",
        item(null, null, new FieldDisplayRuleDTO(a, "SOMETIMES", List.of()), string("x")));
    assertThat(activeFields(formMgr.get(form.getId(), user))).isEmpty();
  }

  @Test
  public void optionChecksApplyOnlyToNewOrChangedOptions() {
    RSForm form = formWith(radio("radio", YES_NO));
    // an option containing '&', stored unchecked, as the forms API allowed
    RadioFieldForm stored = (RadioFieldForm) activeFields(form).get(0);
    stored.setRadioOption("fieldChoices=R&D&fieldChoices=QA");

    List<FormFieldSaveItem> current = current(form);
    ((RadioFieldDTO<?>) current.get(0).getSource()).setName("renamed");
    formMgr.saveFields(form.getId(), current, user);
    assertEquals("renamed", activeFields(formMgr.get(form.getId(), user)).get(0).getName());

    List<FormFieldSaveItem> changed = current(form);
    ((RadioFieldDTO<?>) changed.get(0).getSource()).setRadioValues("fieldChoices=x&y");
    assertThrows(
        FormFieldsValidationException.class, () -> formMgr.saveFields(form.getId(), changed, user));

    FormFieldSaveItem newRadio = item(null, null, null, radio("new", "fieldChoices=p&q"));
    assertThrows(
        FormFieldsValidationException.class,
        () -> formMgr.saveFields(form.getId(), List.of(current(form).get(0), newRadio), user));
  }

  @Test
  public void apiOptionsAreCheckedOneByOneAndUnchangedStoredOptionsAreExempt() {
    for (List<String> options :
        List.of(List.of("A&fieldChoices=B"), List.of("A&"), List.of("A", " "), List.of("a=b"))) {
      RadioFieldPost radio = apiField(new RadioFieldPost(), "radio");
      radio.setOptions(options);
      ChoiceFieldPost choice = apiField(new ChoiceFieldPost(), "choice");
      choice.setOptions(options);
      for (FormFieldPost<?> field : List.<FormFieldPost<?>>of(radio, choice)) {
        FormFieldsValidationException e =
            assertThrows(
                FormFieldsValidationException.class,
                () -> formMgr.createForm(definition(field), user));
        assertThat(e.getErrors())
            .anySatisfy(msg -> assertThat(msg).contains("blank or contains '&' or '='"));
      }
    }

    RSForm form = formMgr.createForm(definition(radioPost("radio", null)), user);
    RadioFieldForm stored = (RadioFieldForm) activeFields(form).get(0);
    // a blank option, stored unchecked, as the forms API allowed
    stored.setRadioOption("fieldChoices=Yes&fieldChoices= ");
    RadioFieldPost unchanged = apiField(new RadioFieldPost(), "renamed");
    unchanged.setId(stored.getId());
    unchanged.setOptions(List.of("Yes", " "));
    RSForm edited =
        formMgr.editForm(form.getId(), definition(unchanged), user, anySessionTracker());
    assertEquals("renamed", activeFields(edited).get(0).getName());

    unchanged.setOptions(List.of("Yes", " ", "No"));
    assertThrows(
        FormFieldsValidationException.class,
        () -> formMgr.editForm(form.getId(), definition(unchanged), user, anySessionTracker()));
  }

  @Test
  public void emptyEntriesInTheFieldListAreFieldErrors() {
    RSForm form = formWith(string("one"));
    List<FormFieldSaveItem> withEmptyEntry = new ArrayList<>(current(form));
    withEmptyEntry.add(null);

    FormFieldsValidationException e =
        assertThrows(
            FormFieldsValidationException.class,
            () -> formMgr.saveFields(form.getId(), withEmptyEntry, user));
    assertThat(e.getErrors()).containsExactly("Field #2 in the list is empty");
    List<FormFieldSaveItem> apiFields = new ArrayList<>();
    apiFields.add(null);
    FormDefinitionCommand apiDefinition =
        new FormDefinitionCommand("api form", null, FormType.NORMAL, apiFields);
    assertThrows(
        FormFieldsValidationException.class, () -> formMgr.createForm(apiDefinition, user));
  }

  @Test
  public void fieldsCopiedFromThePublishedVersionCannotBeDeletedByOmission() {
    RSForm form = publishedFormWith(string("one"), string("two"));
    RSForm draft = formMgr.getForEditing(form.getId(), user, anySessionTracker());
    List<FormFieldSaveItem> current = current(draft);

    FormFieldsValidationException e =
        assertThrows(
            FormFieldsValidationException.class,
            () -> formMgr.saveFields(draft.getId(), List.of(current.get(0)), user));
    assertThat(e.getErrors()).anySatisfy(msg -> assertThat(msg).contains("cannot be deleted"));

    // a field added to the draft can be deleted again
    List<FormFieldSaveItem> withNew = new ArrayList<>(current);
    withNew.add(item(null, null, null, string("added")));
    formMgr.saveFields(draft.getId(), withNew, user);
    RSForm saved = formMgr.saveFields(draft.getId(), current(draft).subList(0, 2), user);
    assertThat(activeFields(saved)).extracting(FieldForm::getName).containsExactly("one", "two");
  }

  @Test
  public void onlyTheFormBeingEditedCanBeSaved() {
    RSForm published = publishedFormWith(string("one"));
    List<FormFieldSaveItem> current = current(published);
    assertThrows(
        IllegalStateException.class, () -> formMgr.saveFields(published.getId(), current, user));
  }

  @Test
  public void saveIsRejectedWhileAnotherUserEditsTheDraftOrItsOriginal() {
    User other = createAndSaveUserIfNotExists(getRandomAlphabeticString("other"));
    RSForm form = publishedFormWith(string("one"));
    RSForm draft = formMgr.getForEditing(form.getId(), user, anySessionTracker());
    formMgr.unlockForm(form.getId(), user);
    List<FormFieldSaveItem> current = current(draft);

    for (Long lockedId : List.of(form.getId(), draft.getId())) {
      recordTracker.attemptToEdit(lockedId, other, new UserSessionTracker(), () -> "other");
      assertThrows(
          IllegalStateException.class, () -> formMgr.saveFields(draft.getId(), current, user));
      recordTracker.unlockRecord(formMgr.get(lockedId, user), other, () -> "other");
    }
    assertNotNull(formMgr.saveFields(draft.getId(), current, user));
  }

  @Test
  public void saveKeepsTheEditLock() {
    RSForm form = publishedFormWith(string("one"));
    RSForm draft = formMgr.getForEditing(form.getId(), user, anySessionTracker());
    assertEquals(user.getUsername(), recordTracker.isEditing(form).orElse(null));

    formMgr.saveFields(draft.getId(), current(draft), user);

    assertEquals(user.getUsername(), recordTracker.isEditing(form).orElse(null));
    formMgr.unlockForm(form.getId(), user);
  }

  @Test
  public void aRevisionCheckedActionOnAFormThatIsMissingOrNotTheUsersIsNotAConflict()
      throws Exception {
    long missing = Long.MAX_VALUE;
    assertThrows(
        ObjectRetrievalFailureException.class, () -> formMgr.publish(missing, null, 0, user));
    assertThrows(
        ObjectRetrievalFailureException.class, () -> formMgr.updateVersion(missing, 0, user));

    User other = createAndSaveUserIfNotExists(getRandomAlphabeticString("other"));
    logoutAndLoginAs(other);
    RSForm othersForm = formMgr.create(other);
    logoutAndLoginAs(user);
    assertThrows(
        AuthorizationException.class, () -> formMgr.publish(othersForm.getId(), null, 99, user));
  }

  @Test
  public void anotherUsersDraftCannotBeMadeTheCurrentVersion() {
    User other = createAndSaveUserIfNotExists(getRandomAlphabeticString("other"));
    logoutAndLoginAs(other);
    RSForm othersForm = formMgr.create(other);
    formMgr.saveFields(othersForm.getId(), List.of(item(null, null, null, string("one"))), other);
    formMgr.publish(othersForm.getId(), true, null, other);
    RSForm draft = formMgr.getForEditing(othersForm.getId(), other, anySessionTracker());
    formMgr.unlockForm(othersForm.getId(), other);
    long revision = revisionOf(draft, other);
    logoutAndLoginAs(user);

    long stale = revision + 1;
    for (long expected : List.of(stale, revision)) {
      assertThrows(
          AuthorizationException.class, () -> formMgr.updateVersion(draft.getId(), expected, user));
    }
    assertThrows(AuthorizationException.class, () -> formMgr.updateVersion(draft.getId(), user));

    flushDatabaseState();
    assertTrue(formMgr.get(draft.getId(), user).isTemporary());
    assertEquals(draft.getId(), formMgr.get(othersForm.getId(), user).getTempForm().getId());
    logoutAndLoginAs(other);
    assertEquals(revision, revisionOf(draft, other));
  }

  @Test
  public void formVersionsAndCopiesKeepKeysAndRules() {
    String gmoKey = newKey();
    RSForm form = formMgr.create(user);
    formMgr.saveFields(
        form.getId(),
        List.of(
            item(null, gmoKey, null, radio("GMO?", YES_NO)),
            item(null, null, showWhen(gmoKey, "Yes"), string("Permit"))),
        user);
    form = formMgr.publish(form.getId(), true, null, user);
    List<FieldForm> originalFields = activeFields(form);

    RSForm draft = formMgr.getForEditing(form.getId(), user, anySessionTracker());
    assertKeysAndRules(originalFields, activeFields(draft));
    RSForm newVersion = formMgr.updateVersion(draft.getId(), user);
    assertKeysAndRules(originalFields, activeFields(newVersion));
    assertNotEquals(form.getId(), newVersion.getId());

    RSForm copy =
        formMgr.copy(newVersion.getId(), user, new CopyIndependentFormAndFieldFormPolicy());
    assertKeysAndRules(originalFields, activeFields(copy));
  }

  @Test
  public void apiEditOfAnUnpublishedFormRenamesANumberField() {
    NumberFieldPost number = apiField(new NumberFieldPost(), "n1");
    RSForm form = formMgr.createForm(definition(number), user);
    Long fieldId = activeFields(form).get(0).getId();

    NumberFieldPost renamed = apiField(new NumberFieldPost(), "n2");
    renamed.setId(fieldId);
    RSForm edited = formMgr.editForm(form.getId(), definition(renamed), user, anySessionTracker());

    assertEquals(form.getId(), edited.getId());
    assertEquals(fieldId, activeFields(edited).get(0).getId());
    assertEquals("n2", activeFields(edited).get(0).getName());
  }

  @Test
  public void apiEditKeepsAnOmittedRuleAndClearsAnExplicitNullOne() {
    String gmoKey = newKey();
    RadioFieldPost gmo = radioPost("GMO?", gmoKey);
    StringFieldPost permit = apiField(new StringFieldPost(), "Permit");
    permit.setDisplayRule(showWhen(gmoKey, "Yes"));
    RSForm form = formMgr.createForm(definition(gmo, permit), user);
    List<FieldForm> fields = activeFields(form);
    String rule = fields.get(1).getDisplayRule();
    assertNotNull(rule);

    RadioFieldPost gmoAgain = radioPost("GMO?", null);
    gmoAgain.setId(fields.get(0).getId());
    StringFieldPost permitWithoutRule = apiField(new StringFieldPost(), "Permit");
    permitWithoutRule.setId(fields.get(1).getId());
    RSForm edited =
        formMgr.editForm(
            form.getId(), definition(gmoAgain, permitWithoutRule), user, anySessionTracker());
    assertEquals(rule, activeFields(edited).get(1).getDisplayRule());

    permitWithoutRule.setDisplayRule(null);
    edited =
        formMgr.editForm(
            form.getId(), definition(gmoAgain, permitWithoutRule), user, anySessionTracker());
    assertNull(activeFields(edited).get(1).getDisplayRule());
  }

  @Test
  public void apiEditAfterASavedDraftReorderMapsOriginalIdsThroughTheDraftLinks() {
    RSForm form = publishedFormWith(string("A"), string("B"), string("C"));
    List<FieldForm> originals = activeFields(form);
    RSForm draft = formMgr.getForEditing(form.getId(), user, anySessionTracker());
    List<FormFieldSaveItem> draftFields = current(draft);
    // the saved draft now lists C, A, B
    formMgr.saveFields(
        draft.getId(), List.of(draftFields.get(2), draftFields.get(0), draftFields.get(1)), user);
    formMgr.unlockForm(form.getId(), user);

    // the API client still sends the published version's ids, renaming A only
    StringFieldPost a = apiField(new StringFieldPost(), "A renamed");
    a.setId(originals.get(0).getId());
    StringFieldPost b = apiField(new StringFieldPost(), "B");
    b.setId(originals.get(1).getId());
    StringFieldPost c = apiField(new StringFieldPost(), "C");
    c.setId(originals.get(2).getId());
    RSForm newVersion =
        formMgr.editForm(form.getId(), definition(a, b, c), user, anySessionTracker());

    List<FieldForm> versioned = activeFields(newVersion);
    assertThat(versioned).extracting(FieldForm::getName).containsExactly("A renamed", "B", "C");
    assertThat(versioned)
        .extracting(FieldForm::getFieldKey)
        .containsExactlyElementsOf(originals.stream().map(FieldForm::getFieldKey).toList());
    assertNotEquals(form.getId(), newVersion.getId());
  }

  @Test
  public void apiEditRejectsUnknownAndRepeatedOriginalIds() {
    RSForm form = publishedFormWith(string("A"));
    Long id = activeFields(form).get(0).getId();
    StringFieldPost unknown = apiField(new StringFieldPost(), "A");
    unknown.setId(id + 100_000);
    assertThrows(
        IllegalArgumentException.class,
        () -> formMgr.editForm(form.getId(), definition(unknown), user, anySessionTracker()));

    StringFieldPost first = apiField(new StringFieldPost(), "A");
    first.setId(id);
    StringFieldPost second = apiField(new StringFieldPost(), "A again");
    second.setId(id);
    assertThrows(
        IllegalArgumentException.class,
        () -> formMgr.editForm(form.getId(), definition(first, second), user, anySessionTracker()));
  }

  @Test
  public void singleFieldEditsAdvanceTheFieldsRevision() {
    RSForm form = formMgr.create(user);
    long start = revisionOf(form);

    StringFieldForm a =
        formMgr.createFieldForm(
            new StringFieldDTO<StringFieldForm>("A", false, "no", ""), form.getId(), user);
    assertEquals(start + 1, revisionOf(form));
    formMgr.updateFieldForm(
        new StringFieldDTO<StringFieldForm>("A2", false, "no", ""), a.getId(), user);
    assertEquals(start + 2, revisionOf(form));
    StringFieldForm b =
        formMgr.createFieldForm(
            new StringFieldDTO<StringFieldForm>("B", false, "no", ""), form.getId(), user);
    formMgr.reorderFields(form.getId(), List.of(b.getId(), a.getId()), user);
    assertEquals(start + 4, revisionOf(form));
    formMgr.deleteFieldFromForm(a.getId(), user);
    assertEquals(start + 5, revisionOf(form));
  }

  @Test
  public void apiCreateAndEditAdvanceTheFieldsRevision() {
    RSForm form = formMgr.createForm(definition(apiField(new NumberFieldPost(), "n1")), user);
    long created = revisionOf(form);
    assertTrue(created > 0, "the API's save of the new form's fields advances its revision");

    NumberFieldPost renamed = apiField(new NumberFieldPost(), "n2");
    renamed.setId(activeFields(form).get(0).getId());
    formMgr.editForm(form.getId(), definition(renamed), user, anySessionTracker());

    assertEquals(created + 1, revisionOf(form));
  }

  @Test
  public void signingIsBlockedOnlyByShownEmptyMandatoryFields() throws Exception {
    String gmoKey = newKey();
    RadioFieldDTO<RadioFieldForm> gmo = radio("GMO?", YES_NO);
    gmo.setRadioSelected("No");
    StringFieldDTO<?> permit = string("Permit");
    permit.setMandatory(true);
    RSForm form = formMgr.create(user);
    formMgr.saveFields(
        form.getId(),
        List.of(item(null, gmoKey, null, gmo), item(null, null, showWhen(gmoKey, "Yes"), permit)),
        user);
    formMgr.publish(form.getId(), true, null, user);

    StructuredDocument doc =
        recordMgr.createNewStructuredDocument(
            folderMgr.getRootFolderForUser(user).getId(), form.getId(), user);
    assertTrue(doc.isAllFieldsValid(), "the empty mandatory field is hidden");

    Field gmoField = doc.getFields().get(0);
    doc = saveDocumentField(doc, gmoField, "Yes");
    assertFalse(doc.isAllFieldsValid(), "the empty mandatory field is now shown");

    doc = saveDocumentField(doc, doc.getFields().get(1), "PERMIT-1");
    assertTrue(doc.isAllFieldsValid());

    // an emptied mandatory field blocks signing while shown, and stops once hidden
    doc = saveDocumentField(doc, doc.getFields().get(1), "");
    assertFalse(doc.isAllFieldsValid());
    doc = saveDocumentField(doc, doc.getFields().get(0), "No");
    assertTrue(doc.isAllFieldsValid());
  }

  @Test
  public void mandatoryFieldsWithoutRulesStillBlockSigning() {
    StringFieldDTO<?> mandatory = string("required");
    mandatory.setMandatory(true);
    RSForm form = formWith(mandatory);
    formMgr.publish(form.getId(), true, null, user);
    StructuredDocument doc =
        recordMgr.createNewStructuredDocument(
            folderMgr.getRootFolderForUser(user).getId(), form.getId(), user);
    assertFalse(doc.isAllFieldsValid());
  }

  private StructuredDocument saveDocumentField(StructuredDocument doc, Field field, String data)
      throws Exception {
    recordMgr.requestRecordEdit(doc.getId(), user, anySessionTracker());
    recordMgr.saveTemporaryDocument(field, user, data);
    recordMgr.saveStructuredDocument(doc.getId(), user.getUsername(), true, new ErrorList());
    return (StructuredDocument) recordMgr.get(doc.getId());
  }

  private void assertKeysAndRules(List<FieldForm> expected, List<FieldForm> actual) {
    assertThat(actual)
        .extracting(FieldForm::getFieldKey)
        .containsExactlyElementsOf(expected.stream().map(FieldForm::getFieldKey).toList());
    assertThat(actual)
        .extracting(FieldForm::getDisplayRule)
        .containsExactlyElementsOf(expected.stream().map(FieldForm::getDisplayRule).toList());
  }

  private void assertRejected(RSForm form, String expectedText, FormFieldSaveItem... fields) {
    FormFieldsValidationException e =
        assertThrows(
            FormFieldsValidationException.class,
            () -> formMgr.saveFields(form.getId(), List.of(fields), user));
    assertThat(e.getErrors()).anySatisfy(msg -> assertThat(msg).contains(expectedText));
  }

  private AbstractFormFieldDTO<?>[] fullyConfiguredFields() {
    NumberFieldDTO<?> number =
        new NumberFieldDTO<>("1.0", "10.0", "2", "5.0", FieldType.NUMBER, "number", true);
    StringFieldDTO<?> string = new StringFieldDTO<>("string", true, "yes", "abc");
    TextFieldDTO<?> text = new TextFieldDTO<>("text", true, "<p>hello</p>");
    RadioFieldDTO<?> radio = new RadioFieldDTO<>(YES_NO, "No", "radio", true, true, true);
    ChoiceFieldDTO<?> choice =
        new ChoiceFieldDTO<>(
            "fieldChoices=a&fieldChoices=b&fieldChoices=c",
            "yes",
            "fieldChoices=a&fieldChoices=c",
            "choice",
            true);
    DateFieldDTO<?> date =
        new DateFieldDTO<>("2024-03-21", "2024-01-01", "2024-12-31", "yyyy-MM-dd", "date", true);
    TimeFieldDTO<?> time = new TimeFieldDTO<>("10:30", "08:00", "18:00", "HH:mm", "time", true);
    return new AbstractFormFieldDTO<?>[] {
      number,
      string,
      text,
      radio,
      choice,
      date,
      time,
      new UriFieldDTO("uri", true),
      new ReferenceFieldDTO("reference", true),
      new AttachmentFieldDTO("attachment", true)
    };
  }

  private RSForm formWith(AbstractFormFieldDTO<?>... sources) {
    RSForm form = formMgr.create(user);
    List<FormFieldSaveItem> fields = new ArrayList<>();
    for (AbstractFormFieldDTO<?> source : sources) {
      fields.add(item(null, null, null, source));
    }
    return formMgr.saveFields(form.getId(), fields, user);
  }

  private RSForm publishedFormWith(AbstractFormFieldDTO<?>... sources) {
    RSForm form = formWith(sources);
    return formMgr.publish(form.getId(), true, null, user);
  }

  /** The form's fields as the editor reads them. */
  private List<FormFieldSaveItem> current(RSForm form) {
    List<FormFieldSaveItem> items = new ArrayList<>();
    for (FieldForm field : activeFields(form)) {
      FormFieldEditorItem read = FormFieldEditorItem.from(field, true);
      items.add(
          new FormFieldSaveItem(
              read.getId(),
              read.getType(),
              read.getFieldKey(),
              read.getDisplayRule(),
              LegacyFormFieldDTOs.fromFieldForm(field)));
    }
    return items;
  }

  private static List<FieldForm> activeFields(RSForm form) {
    return form.getFieldForms().stream().filter(f -> !f.isDeleted()).toList();
  }

  private static FormFieldSaveItem item(
      Long id, String key, FieldDisplayRuleDTO rule, AbstractFormFieldDTO<?> source) {
    String type = source.createFieldForm().getType().getType();
    return new FormFieldSaveItem(id, type, key, rule, source);
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
    return new FormDefinitionCommand("api form", "tag", FormType.NORMAL, items);
  }

  private static <T extends FormFieldPost<?>> T apiField(T field, String name) {
    field.setName(name);
    return field;
  }

  private static RadioFieldPost radioPost(String name, String key) {
    RadioFieldPost radio = apiField(new RadioFieldPost(), name);
    radio.setOptions(List.of("Yes", "No"));
    radio.setFieldKey(key);
    return radio;
  }

  private static RadioFieldDTO<RadioFieldForm> radio(String name, String options) {
    return new RadioFieldDTO<>(options, "", name, false, false);
  }

  private static StringFieldDTO<?> string(String name) {
    return new StringFieldDTO<>(name, false, "no", "");
  }

  private static NumberFieldDTO<?> number(String name) {
    return new NumberFieldDTO<>("", "", "", "", FieldType.NUMBER, name);
  }

  private static FieldDisplayRuleDTO showWhen(String key, String... values) {
    return new FieldDisplayRuleDTO(key, "IS_ANY_OF", List.of(values));
  }

  private static FieldDisplayRuleDTO hasValue(String key) {
    return new FieldDisplayRuleDTO(key, "HAS_VALUE", List.of());
  }

  /** The column is not mapped, so flush what the test's transaction wrote before reading it. */
  private long revisionOf(RSForm form) {
    return revisionOf(form, user);
  }

  private long revisionOf(RSForm form, User reader) {
    flushDatabaseState();
    return formMgr.getForFieldEditor(form.getId(), reader).fieldsRevision();
  }

  private static String newKey() {
    return UUID.randomUUID().toString();
  }
}
