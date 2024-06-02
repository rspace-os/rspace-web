/** RSpace API Access your RSpace documents programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import com.researchspace.model.EcatMediaFile;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * An image, attachment or linked resource object, but without its binary data.
 *
 * <p>It is an API representation of an EcatMediaFile entity.
 *
 * <p>
 *
 * @implementation ApiFile is cached. It is almost immutable apart from the <em>name</em> property.
 *     Renaming triggers eviction from the Spring cache using an event/listener mechanism. <br>
 *     If new mutable properties are added in future, a similar mechanism will need to be used to
 *     indicate that the cached ApiFile should be evicted.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonPropertyOrder(
    value = {
      "id",
      "globalId",
      "name",
      "caption",
      "contentType",
      "created",
      "size",
      "version",
      "_links"
    })
public class ApiFile extends IdentifiableNameableApiObject {

  @JsonProperty("contentType")
  private String contentType = null;

  @JsonProperty("caption")
  private String caption = null;

  @JsonProperty("size")
  private Long size = null;

  @JsonProperty("version")
  private Integer version = 1;

  /** This is stored as millis and serialised/deserialised to IS0-8601 in UTC time. */
  @JsonProperty("created")
  @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long createdMillis;

  public ApiFile(EcatMediaFile emf) {
    setId(emf.getId());
    setGlobalId(emf.getGlobalIdentifier());
    setName(emf.getName());
    setCaption(emf.getDescription());
    setContentType(emf.getContentType());
    setCreatedMillis(emf.getCreationDateMillis());
    setSize(emf.getSize());
    setVersion(Long.valueOf(emf.getVersion()).intValue());
  }
}
