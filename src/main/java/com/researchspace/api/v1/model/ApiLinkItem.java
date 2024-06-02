/** RSpace API Access your RSpace documents programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.http.HttpMethod;

/** A metadata link */
@Data
@Builder
@AllArgsConstructor
@EqualsAndHashCode
@JsonPropertyOrder(value = {"operation", "link", "method", "rel"})
public class ApiLinkItem {

  public static final String NEXT_REL = "next";
  public static final String PREV_REL = "prev";
  public static final String FIRST_REL = "first";
  public static final String LAST_REL = "last";
  public static final String SELF_REL = "self";
  public static final String ENCLOSURE_REL = "enclosure";
  public static final String MONITOR_REL = "monitor";
  public static final String ABOUT_REL = "about";
  public static final String IMAGE_REL = "image";
  public static final String THUMBNAIL_REL = "thumbnail";
  public static final String ICON_REL = "icon";
  public static final String LOCATIONS_IMAGE_REL = "locationsImage";
  public static final String CHEMICAL_IMAGE_REL = "chemicalImage";
  public static final String DOWNLOAD_LINK_REL = "downloadLink";

  @JsonInclude(Include.NON_NULL)
  @JsonProperty("link")
  private String link = null;

  @JsonInclude(Include.NON_NULL)
  @JsonProperty("rel")
  private String rel = null;

  @JsonInclude(Include.NON_NULL)
  @JsonProperty("method")
  private HttpMethod method = null;

  @JsonInclude(Include.NON_NULL)
  @JsonProperty("operation")
  private String operation = null; // copy/paste

  public ApiLinkItem() {}
}
