/** RSpace API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import com.researchspace.model.User;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.field.ExtraField;
import com.researchspace.model.record.IActiveUserStrategy;
import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

/** An ad-hoc field attached to Inventory Record. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString(
    callSuper = true,
    of = {"type"})
@JsonPropertyOrder({
  "id",
  "globalId",
  "name",
  "lastModified",
  "modifiedBy",
  "type",
  "content",
  "parentGlobalId",
  "_links"
})
public class ApiExtraField extends IdentifiableNameableApiObject {

  @JsonProperty("type")
  private ExtraFieldTypeEnum type;

  @JsonProperty("content")
  private String content;

  @JsonProperty("lastModified")
  @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long lastModifiedMillis;

  @JsonProperty("modifiedBy")
  private String modifiedBy;

  @JsonProperty("deleted")
  private Boolean deleted;

  @JsonProperty("parentGlobalId")
  private String parentGlobalId;

  /* to use when manipulating extra field on manager/controller level, but not sent to front-end */
  @JsonIgnore private ApiInventoryRecordInfo parentRecordInfo;

  @JsonProperty(value = "newFieldRequest", access = Access.WRITE_ONLY)
  private boolean newFieldRequest;

  @JsonProperty(value = "deleteFieldRequest", access = Access.WRITE_ONLY)
  private boolean deleteFieldRequest;

  /** The data type of this field */
  public enum ExtraFieldTypeEnum {
    @JsonProperty("text")
    TEXT("text"),

    @JsonProperty("number")
    NUMBER("number");

    private String value;

    ExtraFieldTypeEnum(String value) {
      this.value = value;
    }

    FieldType toFieldTypeEnum() {
      return FieldType.valueOf(toString().toUpperCase());
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }
  }

  public ApiExtraField(ExtraFieldTypeEnum type) {
    setType(type);
  }

  public ApiExtraField(ExtraField field) {
    setId(field.getId());
    setType(ExtraFieldTypeEnum.valueOf(field.getType().toString().toUpperCase()));
    setName(field.getName());
    setLastModifiedMillis(field.getModificationDate().getTime());
    setModifiedBy(field.getModifiedBy());
    setDeleted(field.isDeleted());
    setContent(field.getData());
    setGlobalId(field.getOid().toString());
    if (field.getConnectedRecordOid() != null) {
      setParentGlobalId(field.getConnectedRecordGlobalIdentifier());
      setParentRecordInfo(ApiInventoryRecordInfo.fromInventoryRecord(field.getInventoryRecord()));
    }
  }

  public boolean applyChangesToDatabaseExtraField(ExtraField dbField, User user) {
    boolean contentChanged = false;
    if (StringUtils.isNotBlank(getName())) {
      if (!getName().equals(dbField.getName())) {
        dbField.setName(getName());
        contentChanged = true;
      }
    }
    if (getContent() != null) {
      if (!getContent().equals(dbField.getData())) {
        dbField.setData(getContent());
        contentChanged = true;
      }
    }
    if (contentChanged) {
      dbField.setModificationDate(new Date());
      dbField.setModifiedBy(user.getUsername(), IActiveUserStrategy.CHECK_OPERATE_AS);
    }
    return contentChanged;
  }

  /**
   * TEXT is default (if not provided)
   *
   * @return
   */
  @JsonIgnore
  public FieldType getTypeAsFieldType() {
    return type == null ? FieldType.TEXT : type.toFieldTypeEnum();
  }
}
