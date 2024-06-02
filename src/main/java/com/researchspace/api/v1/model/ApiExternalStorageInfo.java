package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

/**
 * An image, attachment or linked resource object, but without its binary data.
 *
 * <p>It is an API representation of the response from the GalleryApi when called without any
 * parameter will return infos regarding the end points that can be called to move_copy a file into
 * an external storage
 *
 * <p>
 */
@Data
@Builder
@AllArgsConstructor
@ToString(callSuper = true)
@JsonPropertyOrder(value = {"serverUrl", "configuredLocations", "_links"})
public class ApiExternalStorageInfo extends LinkableApiObject {

  @JsonProperty("serverUrl")
  private String serverUrl;

  @JsonInclude(Include.NON_NULL)
  @JsonProperty("configuredLocations")
  private Set<ApiConfiguredLocation> configuredLocations;

  public ApiExternalStorageInfo() {}
}
