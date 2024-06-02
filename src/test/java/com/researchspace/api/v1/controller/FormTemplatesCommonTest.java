package com.researchspace.api.v1.controller;

import static com.researchspace.core.util.DateUtil.localDateToDateUTC;
import static com.researchspace.model.dtos.AbstractFormFieldDTO.MAX_NAME_LENGTH;
import static org.apache.commons.lang.RandomStringUtils.randomAlphabetic;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.api.v1.controller.FormTemplatesCommon.DateFieldPost;
import com.researchspace.api.v1.controller.FormTemplatesCommon.FormFieldPost;
import com.researchspace.api.v1.controller.FormTemplatesCommon.LinkTemplateProvider;
import com.researchspace.api.v1.model.ApiForm;
import com.researchspace.core.testutil.JavaxValidatorTest;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.User;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.TestFactory;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.FormManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.context.support.StaticMessageSource;

public class FormTemplatesCommonTest extends JavaxValidatorTest {

  public @Rule MockitoRule rule = MockitoJUnit.rule();
  @Mock FormManager formMgr;
  @Mock IPropertyHolder properties;
  @Mock IPermissionUtils perms;

  @InjectMocks FormsApiController formController;

  @InjectMocks FormTemplatesCommon commonFormTemplates;
  User subject;
  Folder root = null;

  StaticMessageSource msg = new StaticMessageSource();

  @Test
  public void jsonRoundTrip() {
    FormTemplatesCommon.FormPost formPost = createValidFormPost();
    String jsonBody = JacksonUtil.toJson(formPost);
    // log.info(jsonBody);
    FormTemplatesCommon.FormPost regenerated =
        JacksonUtil.fromJson(jsonBody, FormTemplatesCommon.FormPost.class);
    assertEquals(formPost, regenerated);
    assertTrue(regenerated.getFields().stream().allMatch(ff -> ff.getType() != null));
  }

  @Test
  public void apiSrchConfigValidatior() {
    ApiFormSearchConfig cfg = new ApiFormSearchConfig();
    assertNErrors(cfg, 0, true);
    cfg.setScope("wrong");
    assertNErrors(cfg, 1, true);
    cfg.setScope("all");
    assertNErrors(cfg, 0, true);
  }

  @Test
  public void numberFieldFormValidator() {
    FormTemplatesCommon.NumberFieldPost numberFF = new FormTemplatesCommon.NumberFieldPost();
    numberFF.setMin(5d);
    numberFF.setMax(2d);
    numberFF.setName("test");
    // min >max
    assertNErrors(numberFF, 1, true);

    numberFF.setMax(10d);
    assertNErrors(numberFF, 0, true);

    numberFF.setDefaultValue(2d);
    assertNErrors(numberFF, 1, true);
    numberFF.setDefaultValue(12d);
    assertNErrors(numberFF, 1, true);
    // inclusive ranges
    numberFF.setDefaultValue(5d);
    assertNErrors(numberFF, 0, true);
    numberFF.setDefaultValue(10d);
    assertNErrors(numberFF, 0, true);

    // -ve decimal places
    numberFF.setDecimalPlaces((byte) -1);
    assertNErrors(numberFF, 1, true);
  }

  @Test
  public void typeValidator() {
    FormTemplatesCommon.NumberFieldPost numberFF = new FormTemplatesCommon.NumberFieldPost();
    numberFF.setName("test");
    assertNErrors(numberFF, 0, true);
    numberFF.setType("unknown");
    assertNErrors(numberFF, 1, true);
    numberFF.setType("");
    assertNErrors(numberFF, 1, true);
    numberFF.setType(null);
    assertNErrors(numberFF, 1, true);
  }

  @Test
  public void choiceFieldFormValidator() {
    FormTemplatesCommon.ChoiceFieldPost choiceFF = new FormTemplatesCommon.ChoiceFieldPost();
    choiceFF.setOptions(Arrays.asList(new String[] {"a", "b", "c", "d"}));
    choiceFF.setName("any");
    assertNErrors(choiceFF, 0, true);
    choiceFF.setDefaultOptions(Arrays.asList(new String[] {"a"}));
    assertNErrors(choiceFF, 0, true);
    choiceFF.setDefaultOptions(Arrays.asList(new String[] {"NOT_AN_OPTION"}));
    assertNErrors(choiceFF, 1, true);
    // test non-empty choices - RSPAC1488, require Validator 6 /javax.el 3 which in turn requires
    // TOmcat8
    //		choiceFF.setOptions(Arrays.asList(new String[] { "", "b", null, "d" }));
    //		choiceFF.setDefaultOptions(Arrays.asList(new String[] { "b" }));
    //		assertNErrors(choiceFF, 2, true); // 2 blank fields
  }

  @Test
  public void radioFieldFormValidator() {
    FormTemplatesCommon.RadioFieldPost radioFF = new FormTemplatesCommon.RadioFieldPost();
    radioFF.setOptions(Arrays.asList(new String[] {"a", "b", "c", "d"}));
    radioFF.setName("any");
    assertNErrors(radioFF, 0, true);
    radioFF.setDefaultOption("a");
    assertNErrors(radioFF, 0, true);
    radioFF.setDefaultOption("NOT_AN_OPTION");
    assertNErrors(radioFF, 1, true);
    // - RSPAC1488, require Validator 6 /javax.el 3 which in turn requires TOmcat8
    //		radioFF.setOptions(Arrays.asList(new String[] { "", "b", null, "d" }));
    //		radioFF.setDefaultOption("b");
    //		assertNErrors(radioFF, 2, true); // 2 blank fields
  }

  @Test
  public void dateFieldFormValidator() {
    FormTemplatesCommon.DateFieldPost dateFF = new FormTemplatesCommon.DateFieldPost();

    dateFF.setName("any");
    LocalDate now = LocalDate.now();
    LocalDate past = now.minusDays(5);
    LocalDate future = now.plusDays(10);
    Date defaultDate = localDateToDateUTC(now);
    Date min = localDateToDateUTC(past);
    Date max = localDateToDateUTC(future);
    dateFF.setDefaultValue(defaultDate);
    dateFF.setMax(max);
    dateFF.setMin(min);
    assertNErrors(dateFF, 0, true);

    dateFF.setDefaultValue(localDateToDateUTC(future.plusDays(1)));
    assertNErrors(dateFF, 1, true);
    dateFF.setDefaultValue(null);
    dateFF.setMax(min); // min not before max
    assertNErrors(dateFF, 1, true);

    // default > max
    dateFF.setMin(null);
    dateFF.setDefaultValue(defaultDate);
    assertNErrors(dateFF, 1, true);
    // default < min
    dateFF.setMin(max);
    dateFF.setMax(null);
    dateFF.setDefaultValue(defaultDate);
    assertNErrors(dateFF, 1, true);

    // all null is OK
    dateFF.setDefaultValue(null);
    dateFF.setMin(null);
    assertNErrors(dateFF, 0, true);
  }

  @Test
  public void validateFormPost() {
    FormTemplatesCommon.FormPost formPost = createValidFormPost();
    assertNErrors(formPost, 0, true);
    formPost.getFields().get(0).setName(randomAlphabetic(MAX_NAME_LENGTH + 1));
    assertNErrors(formPost, 1, true);
  }

  final int EXPECTED_LINK_COUNT = 4;

  @Test
  public void newlyCreatedFormCanBeSharedAndPublished() {
    RSForm newForm = createNewForm();
    ApiForm apiForm = new ApiForm(newForm);
    addLinks(newForm, apiForm);
    assertEquals(EXPECTED_LINK_COUNT, apiForm.getLinks().size());
    assertTrue(isPublishLink(apiForm));
    assertTrue(isShareLink(apiForm));
    assertFalse(isUnPublishLink(apiForm));
    assertFalse(isUnShareLink(apiForm));
  }

  @Test
  public void publishedFormCanBeSharedAndUnPublished() {
    RSForm newForm = createNewForm();
    newForm.publish();
    ApiForm apiForm = new ApiForm(newForm);
    addLinks(newForm, apiForm);
    assertEquals(EXPECTED_LINK_COUNT, apiForm.getLinks().size());
    assertFalse(isPublishLink(apiForm));
    assertTrue(isShareLink(apiForm));
    assertTrue(isUnPublishLink(apiForm));
    assertFalse(isUnShareLink(apiForm));
  }

  @Test
  public void sharedFormCanBeUnSharedAndUnPublished() {
    RSForm newForm = createNewForm();
    newForm.publish();
    newForm.getAccessControl().setGroupPermissionType(PermissionType.READ);
    ApiForm apiForm = new ApiForm(newForm);
    addLinks(newForm, apiForm);
    assertEquals(EXPECTED_LINK_COUNT, apiForm.getLinks().size());
    assertFalse(isPublishLink(apiForm));
    assertFalse(isShareLink(apiForm));
    assertTrue(isUnPublishLink(apiForm));
    assertTrue(isUnShareLink(apiForm));
  }

  private void addLinks(RSForm newForm, ApiForm apiForm) {
    Mockito.when(properties.getServerUrl()).thenReturn("http://somewher.com");
    setupMockPermissionsSharingAllowed(newForm);
    LinkTemplateProvider templateSource = formController.createFormLinkTemplateMap();
    commonFormTemplates.addLinks(newForm.getOwner(), newForm, apiForm, templateSource);
  }

  private RSForm createNewForm() {
    RSForm newForm = TestFactory.createAnyForm();
    newForm.setId(1L);
    newForm.getFieldForms().get(0).setId(2L);
    assertTrue(newForm.isNewState());
    return newForm;
  }

  private boolean isUnShareLink(ApiForm apiForm) {
    return linkAndRelMatch(apiForm, "unshare", "share");
  }

  private boolean isUnPublishLink(ApiForm apiForm) {
    return linkAndRelMatch(apiForm, "unpublish", "publish");
  }

  private boolean isShareLink(ApiForm apiForm) {
    return linkAndRelMatch(apiForm, "share", "share")
        && !linkAndRelMatch(apiForm, "unshare", "share");
  }

  private boolean isPublishLink(ApiForm apiForm) {
    return linkAndRelMatch(apiForm, "publish", "publish")
        && !linkAndRelMatch(apiForm, "unpublish", "publish");
  }

  private boolean linkAndRelMatch(ApiForm apiForm, String linkContains, String relEquals) {
    return apiForm.getLinks().stream()
        .anyMatch(link -> link.getLink().contains(linkContains) && link.getRel().equals(relEquals));
  }

  private void setupMockPermissionsSharingAllowed(RSForm newForm) {
    Mockito.when(perms.isPermitted(newForm, PermissionType.SHARE, newForm.getOwner()))
        .thenReturn(true);
  }

  public static FormTemplatesCommon.FormPost createValidFormPost() {
    FormTemplatesCommon.FormPost formPost = new FormTemplatesCommon.FormPost();
    formPost.setName("name");
    formPost.setTags("dd");
    formPost.setFields(createFields());
    return formPost;
  }

  static List<FormFieldPost<? extends FieldForm>> createFields() {
    List<FormFieldPost<? extends FieldForm>> fieldPosts = new ArrayList<>();
    FormTemplatesCommon.ChoiceFieldPost cff = new FormTemplatesCommon.ChoiceFieldPost();
    cff.setMultipleChoice(true);
    cff.setOptions(Arrays.asList(new String[] {"c1", "c2", "c3"}));
    cff.setDefaultOptions(Arrays.asList(new String[] {"c1"}));
    cff.setName("cff");
    FormTemplatesCommon.TextFieldPost tff = new FormTemplatesCommon.TextFieldPost();
    tff.setDefaultValue("some text");
    tff.setName("tff");
    FormTemplatesCommon.StringFieldPost stringFF = new FormTemplatesCommon.StringFieldPost();
    stringFF.setDefaultValue("some string");
    stringFF.setName("sff");
    FormTemplatesCommon.NumberFieldPost numberFF = new FormTemplatesCommon.NumberFieldPost();
    numberFF.setDefaultValue(22d);
    numberFF.setMax(500d);
    numberFF.setMin(5d);
    numberFF.setDecimalPlaces((byte) 2);
    numberFF.setName("numberFF");
    FormTemplatesCommon.RadioFieldPost radioff = new FormTemplatesCommon.RadioFieldPost();
    radioff.setOptions(Arrays.asList(new String[] {"r1", "r2", "r3"}));
    radioff.setDefaultOption("r1");
    radioff.setName("rff");
    FormTemplatesCommon.ReferenceFieldPost refFP = new FormTemplatesCommon.ReferenceFieldPost();
    refFP.setName("reffp");
    FormTemplatesCommon.UriFieldPost uriFP = new FormTemplatesCommon.UriFieldPost();
    uriFP.setName("uriFF");
    FormTemplatesCommon.AttachmentFieldPost attFP = new FormTemplatesCommon.AttachmentFieldPost();
    attFP.setName("attFP");

    fieldPosts.add(cff);
    fieldPosts.add(tff);
    fieldPosts.add(stringFF);
    fieldPosts.add(numberFF);
    fieldPosts.add(radioff);
    fieldPosts.add(createAValidDateForm());
    fieldPosts.add(refFP);
    fieldPosts.add(uriFP);
    fieldPosts.add(attFP);
    return fieldPosts;
  }

  private static DateFieldPost createAValidDateForm() {
    FormTemplatesCommon.DateFieldPost dateff = new FormTemplatesCommon.DateFieldPost();
    LocalDate now = LocalDate.now();
    LocalDate past = now.minusDays(5);
    LocalDate future = now.plusDays(10);
    Date defaultDate = localDateToDateUTC(now);
    Date min = localDateToDateUTC(past);
    Date max = localDateToDateUTC(future);
    dateff.setDefaultValue(defaultDate);
    dateff.setMax(max);
    dateff.setMin(min);
    dateff.setName("rff");
    return dateff;
  }
}
