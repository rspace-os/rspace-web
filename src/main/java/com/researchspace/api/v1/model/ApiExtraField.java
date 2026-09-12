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
   * Identifies which entry of an operation definition produced this field (DevDocs/adr/0007).
   * Resolved field names interpolate user input ({@code {processName}}, {@code {originName}}) and
   * are localized, so the definition key is the field's stable identity: the next run of an
   * operation reads the parent sample's fields over the API and recognises the previous generation
   * by this key to continue from it. Persisted (RSDEV-1231) and returned on GET.
   *
   * <p>READ_ONLY: only the server sets it, when it builds an operation's fields
   * (InventoryOperationRequestBuilder). A value in any request body is ignored at binding rather
   * than rejected, on every endpoint, so a client that GETs a record and sends it back is not 400ed
   * for a value it never chose (that rejection was tried and reverted twice). Ignoring it at
   * binding is what makes "only an operation may claim to have generated a field" true by
   * construction, with no write point or endpoint validator to remember it.
   */
  @JsonProperty(value = "operationFieldKey", access = Access.READ_ONLY)
  private String operationFieldKey;

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
