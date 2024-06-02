package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.record.BaseRecord;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
public abstract class IdentifiableNameableApiObject extends LinkableApiObject
    implements IdentifiableObject {

  @JsonProperty("id")
  private Long id = null;

  @JsonProperty("globalId")
  private String globalId = null;

  @JsonProperty("name")
  @Size(
      max = BaseRecord.DEFAULT_VARCHAR_LENGTH,
      message = "Name cannot be longer than 255 characters")
  private String name = null;

  /**
   * Set id of this object to provided value, unless the object already has an id with different
   * value.
   */
  @JsonIgnore
  public void setIdIfNotSet(Long id) {
    if (this.id == null) {
      setId(id);
    } else if (!this.id.equals(id)) {
      throw new IllegalArgumentException(
          "Id set in api object: " + this.id + " doesn't match provided one: " + id);
    }
  }

  @JsonIgnore
  public GlobalIdentifier getOid() {
    if (globalId == null) {
      return null;
    }
    return new GlobalIdentifier(globalId);
  }
}
