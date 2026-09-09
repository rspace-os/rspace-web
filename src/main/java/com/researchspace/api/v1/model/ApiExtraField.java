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
import com.researchspace.model.inventory.field.ExtraLinkField;
import com.researchspace.model.inventory.field.InventoryLink;
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
  "link",
  "parentGlobalId",
  "_links"
})
public class ApiExtraField extends IdentifiableNameableApiObject {

  @JsonProperty("type")
  private ExtraFieldTypeEnum type;

  @JsonProperty("content")
  private String content;

  @JsonProperty("link")
  private ApiInventoryLink link;

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

  /**
   * Identifies which entry of an operation definition produced this field, so the operations
   * endpoint can whitelist a request against the definition it names (DevDocs/adr/0007). Resolved
   * field names interpolate user input ({@code {processName}}, {@code {originName}}) and are
   * localized, so the definition key travels on the wire instead.
   *
   * <p>Read-write, and persisted (RSDEV-1231). It has to come BACK on GET because it is the field's
   * stable identity across runs of an operation: the next run reads the parent sample's fields over
   * the API and has to recognise the previous generation's field to continue from it, which
   * matching on the localized name cannot do reliably.
   *
   * <p>Only the operations endpoint may SET it. Every other endpoint binding this DTO rejects a
   * non-null value with a field-scoped 400 rather than ignoring it: a silently dropped key would be
   * inconsistent with the caller's intent, and an accepted one would masquerade as
   * operation-created.
   */
  @JsonProperty("operationFieldKey")
  private String operationFieldKey;

  /**
   * Whether {@link #operationFieldKey} has been checked against the operation definition that is
   * allowed to declare it, and may therefore be persisted.
   *
   * <p>{@code @JsonIgnore}, so no client can set it and no other endpoint does: the operations
   * endpoint's validator is the only writer (InventoryOperationPostValidator, which already
   * whitelists every key against the specific operation's definition), and ApiExtraFieldsHelper
   * persists the key only when this is set. That makes "only an operation may claim to have
   * generated a field" true by CONSTRUCTION rather than by every other endpoint remembering to
   * reject one. Enumerating the endpoints to police was tried first and leaked: the sample- and
   * instrument-template validators do not go through the shared extra-field validation at all, so a
   * template request could persist a forged key (parallel review, C1).
   */
  @JsonIgnore private boolean operationFieldKeyVerified;

  /** The data type of this field */
  public enum ExtraFieldTypeEnum {
    @JsonProperty("text")
    TEXT("text"),

    @JsonProperty("number")
    NUMBER("number"),

    @JsonProperty("link")
    LINK("link");

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
    // The stable identity of an operation-generated field, so the next run of that operation can
    // recognise the previous generation's field by key rather than by its localized name.
    setOperationFieldKey(field.getOperationFieldKey());
    if (field instanceof ExtraLinkField && ((ExtraLinkField) field).getLink() != null) {
      setLink(new ApiInventoryLink(((ExtraLinkField) field).getLink()));
    }
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
    if (dbField instanceof ExtraLinkField && link != null) {
      contentChanged |= applyLinkPayload((ExtraLinkField) dbField);
    }
    if (contentChanged) {
      dbField.setModificationDate(new Date());
      dbField.setModifiedBy(user.getUsername(), IActiveUserStrategy.CHECK_OPERATE_AS);
    }
    return contentChanged;
  }

  /**
   * Applies relationType changes from the incoming API payload onto the existing persisted
   * InventoryLink. Target and version-pin changes are applied in the service layer instead
   * (ApiExtraFieldsHelper's applyExistingLinkFieldChanges, which can reach the
   * InventoryLinkManager): both need target validation and a recapture of the pinned audit revision
   * (targetRevisionId), which this DTO cannot perform. Setting the pin here in-place would leave
   * the stored revision pointing at the previously pinned version.
   */
  private boolean applyLinkPayload(ExtraLinkField dbField) {
    InventoryLink dbLink = dbField.getLink();
    if (dbLink == null) {
      return false;
    }
    String newRelation = link.getRelationType();
    if (newRelation != null && !newRelation.equals(dbLink.getRelationType())) {
      dbLink.setRelationType(newRelation);
      return true;
    }
    return false;
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
