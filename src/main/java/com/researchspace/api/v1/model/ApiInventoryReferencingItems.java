package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response wrapper listing the inventory items that reference a target record via a Link
 * extraField. The target may be an Inventory item or an ELN record (document, notebook or gallery
 * file): the referencing-items endpoints serve both.
 */
@Data
@NoArgsConstructor
public class ApiInventoryReferencingItems {

  @JsonProperty("referencingItems")
  private List<ApiInventoryReferencingItem> referencingItems = new ArrayList<>();
}
