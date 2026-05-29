package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response wrapper listing items that reference a target inventory record via a Link extraField.
 */
@Data
@NoArgsConstructor
public class ApiInventoryReferencingItems {

  @JsonProperty("referencingItems")
  private List<ApiInventoryReferencingItem> referencingItems = new ArrayList<>();
}
