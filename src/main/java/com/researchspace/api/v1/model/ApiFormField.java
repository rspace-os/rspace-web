package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
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
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonTypeInfo(use = Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @Type(value = ApiChoiceFormField.class, name = FieldType.CHOICE_TYPE),
  @Type(value = ApiTextFormField.class, name = FieldType.TEXT_TYPE),
  @Type(value = ApiStringFormField.class, name = FieldType.STRING_TYPE),
  @Type(value = ApiNumberFormField.class, name = FieldType.NUMBER_TYPE),
  @Type(value = ApiRadioFormField.class, name = FieldType.RADIO_TYPE),
  @Type(value = ApiDateFormField.class, name = FieldType.DATE_TYPE),
  @Type(value = ApiTimeFormField.class, name = FieldType.TIME_TYPE),
  @Type(value = ApiReferenceFormField.class, name = FieldType.REFERENCE_TYPE),
  @Type(value = ApiUriFormField.class, name = FieldType.URI_TYPE),
  @Type(value = ApiAttachmentFormField.class, name = FieldType.ATTACHMENT_TYPE)
})
public abstract class ApiFormField extends IdentifiableNameableApiObject {

  @JsonProperty("lastModified")
  @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long lastModifiedMillis = null;

  private Integer index;
  private String type;

  public static ApiFormField fromFieldForm(FieldForm formField) {
    switch (formField.getType()) {
      case CHOICE:
        return new ApiChoiceFormField((ChoiceFieldForm) formField);
      case RADIO:
        return new ApiRadioFormField((RadioFieldForm) formField);
      case TEXT:
        return new ApiTextFormField((TextFieldForm) formField);
      case STRING:
        return new ApiStringFormField((StringFieldForm) formField);
      case NUMBER:
        return new ApiNumberFormField((NumberFieldForm) formField);
      case DATE:
        return new ApiDateFormField((DateFieldForm) formField);
      case TIME:
        return new ApiTimeFormField((TimeFieldForm) formField);
      case REFERENCE:
        return new ApiReferenceFormField((ReferenceFieldForm) formField);
      case URI:
        return new ApiUriFormField((URIFieldForm) formField);
      case ATTACHMENT:
        return new ApiAttachmentFormField((AttachmentFieldForm) formField);
      default:
        throw new IllegalArgumentException("unknown form field type: " + formField.getType());
    }
  }

  public ApiFormField(FieldForm formField) {
    super(formField.getId(), formField.getOid().getIdString(), formField.getName());
    setLastModifiedMillis(formField.getModificationDate());
    setIndex(formField.getColumnIndex());
    setType(formField.getType().getType());
  }
}
