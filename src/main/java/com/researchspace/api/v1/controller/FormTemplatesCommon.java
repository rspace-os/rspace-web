package com.researchspace.api.v1.controller;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.google.common.base.Supplier;
import com.researchspace.api.v1.model.IdentifiableObject;
import com.researchspace.api.v1.model.LinkableApiObject;
import com.researchspace.api.v1.model.ValidChoiceFormFieldPost;
import com.researchspace.api.v1.model.ValidDateFormFieldPost;
import com.researchspace.api.v1.model.ValidName;
import com.researchspace.api.v1.model.ValidNumberFormFieldPost;
import com.researchspace.api.v1.model.ValidRadioFormFieldPost;
import com.researchspace.model.User;
import com.researchspace.model.dtos.FormFieldSource;
import com.researchspace.model.field.AttachmentFieldForm;
import com.researchspace.model.field.ChoiceFieldForm;
import com.researchspace.model.field.DateFieldForm;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.field.NumberFieldForm;
import com.researchspace.model.field.RadioFieldForm;
import com.researchspace.model.field.ReferenceFieldForm;
import com.researchspace.model.field.StringFieldForm;
import com.researchspace.model.field.TextFieldForm;
import com.researchspace.model.field.TimeFieldForm;
import com.researchspace.model.field.URIFieldForm;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.AbstractForm;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.FormType;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/** Common utilities for SampleTemplate /Form creation */
@Component
public class FormTemplatesCommon {

  private @Autowired IPermissionUtils permissions;

  @Builder
  @Value
  public static class LinkTemplateProvider {
    private String publish, unpublish, share, unshare, icon, self;
    private Supplier<UriComponentsBuilder> apiBaseUrl;
  }

  /** Common methods needed to create links */
  public static interface ApiFormTemplateLinkSource extends IdentifiableObject {

    Long getIconId();

    LinkableApiObject addLink(String link, String linkType);
  }

  @Data
  public static class FormPost {
    @ValidName private String name;
    private String tags;
    private FormType formType = FormType.NORMAL;
    @Valid private List<FormFieldPost<? extends FieldForm>> fields = new ArrayList<>();
  }

  @JsonTypeInfo(use = Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
  @JsonSubTypes({
    @Type(value = ChoiceFieldPost.class, name = FieldType.CHOICE_TYPE),
    @Type(value = TextFieldPost.class, name = FieldType.TEXT_TYPE),
    @Type(value = StringFieldPost.class, name = FieldType.STRING_TYPE),
    @Type(value = NumberFieldPost.class, name = FieldType.NUMBER_TYPE),
    @Type(value = RadioFieldPost.class, name = FieldType.RADIO_TYPE),
    @Type(value = DateFieldPost.class, name = FieldType.DATE_TYPE),
    @Type(value = TimeFieldPost.class, name = FieldType.TIME_TYPE),
    @Type(value = UriFieldPost.class, name = FieldType.URI_TYPE),
    @Type(value = ReferenceFieldPost.class, name = FieldType.REFERENCE_TYPE),
    @Type(value = AttachmentFieldPost.class, name = FieldType.ATTACHMENT_TYPE)
  })
  @Data()
  @EqualsAndHashCode(callSuper = false)
  @NoArgsConstructor
  public abstract static class FormFieldPost<T extends FieldForm> implements FormFieldSource<T> {
    @Pattern(
        regexp =
            "(Text)|(String)|(Number)|(Radio)|(Choice)|(Date)|(Time)|(Reference)|(Uri)|(Attachment)",
        message =
            "Please supply a supported 'type' property: was '${validatedValue}' but must match"
                + " {regexp} ")
    @NotNull
    private String type;

    FormFieldPost(String type) {
      super();
      this.type = type;
    }

    @ValidName String name;

    // this will always be null for POST,
    Long id;

    // utility method for radoio/choice
    @JsonIgnore
    String getOptionsStringFromList(List<String> optionsList) {
      List<String> optionParts = new ArrayList<>();
      for (String option : optionsList) {
        optionParts.add("fieldChoices=" + option);
      }
      String options = StringUtils.join(optionParts, "&");
      return options;
    }
  }

  @Data()
  @EqualsAndHashCode(callSuper = false)
  @ValidChoiceFormFieldPost
  public static class ChoiceFieldPost extends FormFieldPost<ChoiceFieldForm> {
    public ChoiceFieldPost() {
      super(FieldType.CHOICE_TYPE);
    }

    private boolean multipleChoice;

    @Size(min = 1, message = "Please provide at least one option")
    private List<String> options = new ArrayList<>();

    private List<String> defaultOptions = new ArrayList<>();

    public void copyValuesIntoFieldForm(ChoiceFieldForm choiceFieldForm) {
      setValues(choiceFieldForm);
      choiceFieldForm.setName(name);
    }

    @Override
    public ChoiceFieldForm createFieldForm() {
      ChoiceFieldForm choiceFieldForm = new ChoiceFieldForm(name);
      setValues(choiceFieldForm);
      return choiceFieldForm;
    }

    @JsonIgnore
    private void setValues(ChoiceFieldForm choiceFieldForm) {
      choiceFieldForm.setMultipleChoice(multipleChoice);
      String optionsStr = getOptionsStringFromList(options);
      choiceFieldForm.setChoiceOptions(optionsStr);
      String defaultOptionsStr = getOptionsStringFromList(defaultOptions);
      choiceFieldForm.setDefaultChoiceOption(defaultOptionsStr);
    }
  }

  @Data()
  @EqualsAndHashCode(callSuper = false)
  @ValidRadioFormFieldPost
  public static class RadioFieldPost extends FormFieldPost<RadioFieldForm> {
    public RadioFieldPost() {
      super(FieldType.RADIO_TYPE);
    }

    @Size(min = 1, message = "Please provide at least one option")
    private List<String> options = new ArrayList<>();

    @Size(min = 1)
    private String defaultOption;

    public void copyValuesIntoFieldForm(RadioFieldForm formField) {
      setValues(formField);
      formField.setName(name);
    }

    @Override
    public RadioFieldForm createFieldForm() {
      RadioFieldForm formField = new RadioFieldForm(name);
      setValues(formField);
      return formField;
    }

    @JsonIgnore
    private void setValues(RadioFieldForm formField) {
      String optionsStr = getOptionsStringFromList(options);
      formField.setRadioOption(optionsStr);
      formField.setDefaultRadioOption(defaultOption);
    }
  }

  @Data()
  @EqualsAndHashCode(callSuper = false)
  public static class TextFieldPost extends FormFieldPost<TextFieldForm> {
    public TextFieldPost() {
      super(FieldType.TEXT_TYPE);
    }

    private String defaultValue = "";

    @Override
    public void copyValuesIntoFieldForm(TextFieldForm textFieldForm) {
      textFieldForm.setDefaultValue(defaultValue);
      textFieldForm.setName(name);
    }

    @Override
    public TextFieldForm createFieldForm() {
      TextFieldForm tf = new TextFieldForm(name);
      tf.setDefaultValue(defaultValue);
      return tf;
    }
  }

  @Data()
  @EqualsAndHashCode(callSuper = false)
  public static class StringFieldPost extends FormFieldPost<StringFieldForm> {
    public StringFieldPost() {
      super(FieldType.STRING_TYPE);
    }

    @Size(
        max = BaseRecord.DEFAULT_VARCHAR_LENGTH,
        message =
            "String fields must be less than " + BaseRecord.DEFAULT_VARCHAR_LENGTH + " characters")
    private String defaultValue = "";

    @Override
    public void copyValuesIntoFieldForm(StringFieldForm stringFieldForm) {
      stringFieldForm.setDefaultStringValue(defaultValue);
      stringFieldForm.setName(name);
    }

    @Override
    public StringFieldForm createFieldForm() {
      StringFieldForm tf = new StringFieldForm(name);
      tf.setDefaultStringValue(defaultValue);
      return tf;
    }
  }

  @Data()
  @EqualsAndHashCode(callSuper = false)
  public static class UriFieldPost extends FormFieldPost<URIFieldForm> {
    public UriFieldPost() {
      super(FieldType.URI_TYPE);
    }

    @Override
    public void copyValuesIntoFieldForm(URIFieldForm uriFieldForm) {
      uriFieldForm.setName(name);
    }

    @Override
    public URIFieldForm createFieldForm() {
      URIFieldForm tf = new URIFieldForm(name);
      return tf;
    }
  }

  @Data()
  @EqualsAndHashCode(callSuper = false)
  public static class ReferenceFieldPost extends FormFieldPost<ReferenceFieldForm> {
    public ReferenceFieldPost() {
      super(FieldType.REFERENCE_TYPE);
    }

    @Override
    public void copyValuesIntoFieldForm(ReferenceFieldForm refFieldForm) {
      refFieldForm.setName(name);
    }

    @Override
    public ReferenceFieldForm createFieldForm() {
      ReferenceFieldForm tf = new ReferenceFieldForm(name);
      return tf;
    }
  }

  @Data()
  @EqualsAndHashCode(callSuper = false)
  public static class AttachmentFieldPost extends FormFieldPost<AttachmentFieldForm> {
    public AttachmentFieldPost() {
      super(FieldType.ATTACHMENT_TYPE);
    }

    @Override
    public void copyValuesIntoFieldForm(AttachmentFieldForm fieldForm) {
      fieldForm.setName(name);
    }

    @Override
    public AttachmentFieldForm createFieldForm() {
      AttachmentFieldForm tf = new AttachmentFieldForm(name);
      return tf;
    }
  }

  @Data()
  @EqualsAndHashCode(callSuper = false)
  @AllArgsConstructor
  @ValidDateFormFieldPost
  public static class DateFieldPost extends FormFieldPost<DateFieldForm> {
    public DateFieldPost() {
      super(FieldType.DATE_TYPE);
    }

    private static final String YYYY_MM_DD_ISO8601 = "yyyy-MM-dd";

    @DateTimeFormat(pattern = YYYY_MM_DD_ISO8601)
    private Date defaultValue;

    @DateTimeFormat(pattern = YYYY_MM_DD_ISO8601)
    private Date min;

    @DateTimeFormat(pattern = YYYY_MM_DD_ISO8601)
    private Date max;

    @Override
    public void copyValuesIntoFieldForm(DateFieldForm dateFieldForm) {
      setValues(dateFieldForm);
      dateFieldForm.setName(name);
    }

    @Override
    public DateFieldForm createFieldForm() {
      DateFieldForm dateFieldForm = new DateFieldForm(name);
      setValues(dateFieldForm);
      return dateFieldForm;
    }

    @JsonIgnore
    private void setValues(DateFieldForm dateFieldForm) {
      dateFieldForm.setDefaultDate(defaultValue != null ? defaultValue.getTime() : 0);
      dateFieldForm.setMaxValue(max != null ? max.getTime() : 0);
      dateFieldForm.setMinValue(min != null ? min.getTime() : 0);
    }
  }

  @Data()
  @EqualsAndHashCode(callSuper = false)
  public static class TimeFieldPost extends FormFieldPost<TimeFieldForm> {
    public TimeFieldPost() {
      super(FieldType.TIME_TYPE);
    }

    private Long defaultValue = null;

    @Override
    public void copyValuesIntoFieldForm(TimeFieldForm stringFieldForm) {
      stringFieldForm.setDefaultTime(defaultValue);
      stringFieldForm.setName(name);
    }

    @Override
    public TimeFieldForm createFieldForm() {
      TimeFieldForm tf = new TimeFieldForm(name);
      tf.setDefaultTime(defaultValue);
      return tf;
    }
  }

  @Data()
  @EqualsAndHashCode(callSuper = false)
  @ValidNumberFormFieldPost
  public static class NumberFieldPost extends FormFieldPost<NumberFieldForm> {
    public NumberFieldPost() {
      super(FieldType.NUMBER_TYPE);
    }

    private Double min;
    private Double max;

    @Min(0)
    private Byte decimalPlaces;

    // there is now no default default value, see RSPAC-65
    private Double defaultValue = null;

    @Override
    public void copyValuesIntoFieldForm(NumberFieldForm numberFieldForm) {
      setValues(numberFieldForm);
    }

    @Override
    public NumberFieldForm createFieldForm() {
      NumberFieldForm nf = new NumberFieldForm(name);
      setValues(nf);
      return nf;
    }

    @JsonIgnore
    private void setValues(NumberFieldForm nf) {
      nf.setMinNumberValue(min);
      nf.setMaxNumberValue(max);
      nf.setDefaultNumberValue(defaultValue);
      nf.setDecimalPlaces(decimalPlaces);
    }
  }

  protected String buildParameterisedLink(
      final String path, Map<String, ?> uriVariables, UriComponentsBuilder linkBuilder) {
    return linkBuilder.path(path).buildAndExpand(uriVariables).encode().toUriString();
  }

  void buildAndAddPublishLink(
      String publishLink, ApiFormTemplateLinkSource apiForm, UriComponentsBuilder linkBuilder) {
    String link = buildParameterisedLink(publishLink, getIdMap(apiForm), linkBuilder);
    apiForm.addLink(link, "publish");
  }

  Map<String, Object> getIdMap(ApiFormTemplateLinkSource apiForm) {
    Map<String, Object> idMap = new HashMap<>();
    idMap.put("id", apiForm.getId());
    return idMap;
  }

  void buildAndAddShareLink(
      String shareLink, ApiFormTemplateLinkSource apiForm, UriComponentsBuilder linkBuilder) {
    String link = buildParameterisedLink(shareLink, getIdMap(apiForm), linkBuilder);
    apiForm.addLink(link, "share");
  }

  void addLinks(
      User user,
      AbstractForm form,
      ApiFormTemplateLinkSource apiForm,
      LinkTemplateProvider linkTemplatesMap) {
    Supplier<UriComponentsBuilder> uriSupplier = linkTemplatesMap.getApiBaseUrl();
    // have to call get() each time to get a new URI builder
    buildAndAddSelfLink(linkTemplatesMap.getSelf(), apiForm, uriSupplier.get());
    if (showPublishLink(user, form)) {
      buildAndAddPublishLink(linkTemplatesMap.getPublish(), apiForm, uriSupplier.get());
    } else if (showUnpublishLink(user, form)) {
      buildAndAddPublishLink(linkTemplatesMap.getUnpublish(), apiForm, uriSupplier.get());
    }
    if (showShareLink(user, form)) {
      buildAndAddShareLink(linkTemplatesMap.getShare(), apiForm, uriSupplier.get());
    } else if (showUnshareLink(user, form)) {
      buildAndAddShareLink(linkTemplatesMap.getUnshare(), apiForm, uriSupplier.get());
    }

    buildAndAddIconLink(linkTemplatesMap.getIcon(), apiForm, uriSupplier.get());
  }

  void buildAndAddIconLink(
      String iconLink,
      ApiFormTemplateLinkSource apiForm,
      UriComponentsBuilder baseUriComponentsBuilder) {
    Map<String, Object> params = getIdMap(apiForm);
    params.put("iconId", apiForm.getIconId());
    String link = buildParameterisedLink(iconLink, params, baseUriComponentsBuilder);
    apiForm.addLink(link, "icon");
  }

  boolean showUnpublishLink(User user, AbstractForm form) {
    return isShareOrPublishPermitted(user, form) && isUnPublishable(form);
  }

  boolean showShareLink(User user, AbstractForm form) {
    return isShareOrPublishPermitted(user, form) && isNotSharedWithGroup(form);
  }

  boolean showUnshareLink(User user, AbstractForm form) {
    return isShareOrPublishPermitted(user, form) && isSharedWithGroup(form);
  }

  boolean showPublishLink(User user, AbstractForm form) {
    return isShareOrPublishPermitted(user, form) && isPublishable(form);
  }

  private boolean isShareOrPublishPermitted(User user, AbstractForm form) {
    return permissions.isPermitted(form, PermissionType.SHARE, user);
  }

  private boolean isNotSharedWithGroup(AbstractForm form) {
    return PermissionType.NONE.equals(form.getAccessControl().getGroupPermissionType());
  }

  private boolean isSharedWithGroup(AbstractForm form) {
    return PermissionType.READ.equals(form.getAccessControl().getGroupPermissionType())
        || PermissionType.WRITE.equals(form.getAccessControl().getGroupPermissionType());
  }

  protected void buildAndAddSelfLink(
      final String endpoint, final ApiFormTemplateLinkSource info, UriComponentsBuilder baseUrl) {
    String path = endpoint + "/" + info.getId();
    String link = baseUrl.path(path).build().encode().toUriString();
    info.addSelfLink(link);
  }

  private boolean isPublishable(AbstractForm form) {
    return form.isNewState() || !form.isPublishedAndVisible();
  }

  private boolean isUnPublishable(AbstractForm form) {
    return form.isPublishedAndVisible();
  }
}
