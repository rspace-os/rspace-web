/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.model.inventory.ContainerLocation;
import com.researchspace.model.inventory.InventoryRecord;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class ApiContainerLocationWithContent extends ApiContainerLocation {

  @JsonProperty("content")
  private ApiInventoryRecordInfo content;

  @JsonIgnore private InventoryRecord dbContent;

  public ApiContainerLocationWithContent(Integer coordX, Integer coordY) {
    super(coordX, coordY);
  }

  public ApiContainerLocationWithContent(ContainerLocation location) {
    super(location);

    dbContent = location.getStoredRecord();
    if (dbContent != null) {
      content = ApiInventoryRecordInfo.fromInventoryRecord(dbContent);
    }
  }
}
