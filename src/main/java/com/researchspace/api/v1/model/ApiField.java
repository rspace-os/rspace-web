/** RSpace API Access your RSpace documents programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** A field in ApiDocument, with a list of attached Files. Also inherited by field in ApiSample. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString(
    callSuper = true,
    of = {"type"})
public abstract class ApiField extends IdentifiableNameableApiObject {

  @JsonProperty("type")
  protected ApiFieldType type;

  @JsonProperty("content")
  protected String content;

  @JsonProperty("lastModified")
  @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  protected Long lastModifiedMillis;

  @JsonProperty("columnIndex")
  protected Integer columnIndex;

  /** The data type of this field */
  public enum ApiFieldType {
    @JsonProperty("string")
    STRING("string"),

    @JsonProperty("text")
    TEXT("text"),

    @JsonProperty("choice")
    CHOICE("choice"),

    @JsonProperty("radio")
    RADIO("radio"),

    @JsonProperty("date")
    DATE("date"),

    @JsonProperty("number")
    NUMBER("number"),

    @JsonProperty("time")
    TIME("time"),

    @JsonProperty("attachment")
    ATTACHMENT("attachment"),

    @JsonProperty("reference")
    REFERENCE("reference"),

    @JsonProperty("uri")
    URI("uri"),

    @JsonProperty("identifier")
    IDENTIFIER("identifier");

    private String value;

    ApiFieldType(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }

    @JsonCreator
    public static ApiFieldType fromString(String value) {
      return value == null ? null : ApiFieldType.valueOf(value.toUpperCase());
    }
  }
}
