package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.record.BaseRecord;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
public abstract class IdentifiableNameableApiObject extends LinkableApiObject
    implements IdentifiableObject, UnknownPropertyCapturing {
  /**
   * Names of request properties this object does not declare; see {@link UnknownPropertyCapturing}.
   * Inert here: never serialized, excluded from equals and toString, and inspected only by the
   * operations endpoint's validator.
   */
  @JsonIgnore @EqualsAndHashCode.Exclude @ToString.Exclude
  private final List<String> unknownProperties = new ArrayList<>();

  /** Records an unrecognised property's NAME and discards its value. */
  @JsonAnySetter
  private void captureUnknownProperty(String name, Object ignoredValue) {
    if (unknownProperties.size() < UnknownPropertyCapturing.MAX_CAPTURED_UNKNOWN_PROPERTIES) {
      unknownProperties.add(name);
    }
  }

  @JsonProperty("id")
  private Long id = null;

  @JsonProperty("globalId")
  private String globalId = null;

  @JsonProperty("name")
  @Size(
      max = BaseRecord.DEFAULT_VARCHAR_LENGTH,
      message = "{errors.inventory.name.validationTooLong}")
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
