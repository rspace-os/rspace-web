package com.researchspace.model.dtos.chemistry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PubChemPropertyResponse {

  @JsonProperty("PropertyTable")
  private PropertyTable propertyTable;

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class PropertyTable {

    @JsonProperty("Properties")
    private List<ChemicalProperty> properties;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ChemicalProperty {

    @JsonProperty("CID")
    private Long cid;

    @JsonProperty("Title")
    private String title;

    @JsonProperty("MolecularFormula")
    private String molecularFormula;

    @JsonProperty("SMILES")
    private String smiles;
  }
}
