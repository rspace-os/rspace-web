package com.researchspace.integrations.jove.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URL;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JoveArticle {

  @JsonProperty("accesstype")
  private String accessType;

  private Long id;
  private URL thumbnail;
  private String title;
  private URL url;
  private String section;

  @JsonProperty("embed_url")
  private URL embedUrl;

  private String text;

  @JsonProperty("hasvideo")
  private boolean hasVideo;
}
