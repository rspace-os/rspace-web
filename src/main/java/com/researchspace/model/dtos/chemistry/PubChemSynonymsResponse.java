package com.researchspace.model.dtos.chemistry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PubChemSynonymsResponse {

  @JsonProperty("InformationList")
  private InformationList informationList;

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class InformationList {

    @JsonProperty("Information")
    private List<Information> information;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Information {

    @JsonProperty("CID")
    private Long cid;

    @JsonProperty("Synonym")
    private List<String> synonyms;
  }
}
