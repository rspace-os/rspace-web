/** RSpace API Access your RSpace documents programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Basic information about RSpace Document */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonPropertyOrder(
    value = {
      "id",
      "globalId",
      "name",
      "created",
      "lastModified",
      "parentFolderId",
      "type",
      "_links"
    })
public class RecordTreeItemInfo extends IdentifiableNameableApiObject {

  @JsonProperty("created")
  @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long createdMillis = null;

  @JsonProperty("lastModified")
  @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long lastModifiedMillis = null;

  @JsonProperty("parentFolderId")
  private Long parentFolderId;

  @JsonProperty("owner")
  private ApiUser owner = null;

  @JsonProperty("type")
  private ApiRecordType type = null;

  public RecordTreeItemInfo(BaseRecord record, User authorisedSubject) {
    super(record.getId(), record.getGlobalIdentifier(), record.getName());
    setCreatedMillis(record.getCreationDateMillis());
    setLastModifiedMillis(record.getModificationDateMillis());
    // set parent folder if user is owner of document else null
    if (authorisedSubject.equals(record.getOwner()) && record.hasParents()) {
      setParentFolderId(record.getOwnerParent().get().getId());
    }
    setOwner(new ApiUser(record.getOwner()));
    if (record.isNotebook()) {
      setType(ApiRecordType.NOTEBOOK);
    } else if (record.isFolder()) {
      setType(ApiRecordType.FOLDER);
    } else if (record.isStructuredDocument()) {
      setType(ApiRecordType.DOCUMENT);
    } else if (record.isMediaRecord()) {
      setType(ApiRecordType.MEDIA);
    }
  }
}
