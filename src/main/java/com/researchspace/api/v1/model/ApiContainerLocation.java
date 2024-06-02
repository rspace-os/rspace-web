/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.researchspace.model.inventory.ContainerLocation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class ApiContainerLocation {

  @JsonProperty("id")
  private Long id;

  @JsonProperty("coordX")
  private Integer coordX;

  @JsonProperty("coordY")
  private Integer coordY;

  @JsonProperty(value = "newLocationRequest", access = Access.WRITE_ONLY)
  private boolean newLocationRequest;

  @JsonProperty(value = "deleteLocationRequest", access = Access.WRITE_ONLY)
  private boolean deleteLocationRequest;

  public ApiContainerLocation(ContainerLocation location) {
    id = location.getId();
    coordX = location.getCoordX();
    coordY = location.getCoordY();
  }

  public ApiContainerLocation(Integer coordX, Integer coordY) {
    this.coordX = coordX;
    this.coordY = coordY;
  }

  public boolean applyChangesToDatabaseLocation(ContainerLocation dbLocation) {
    boolean contentChanged = false;

    if (getCoordX() != null && getCoordY() != null) {
      if (!getCoordX().equals(dbLocation.getCoordX())) {
        dbLocation.setCoordX(getCoordX());
        contentChanged = true;
      }
      if (!getCoordY().equals(dbLocation.getCoordY())) {
        dbLocation.setCoordY(getCoordY());
        contentChanged = true;
      }
    }
    return contentChanged;
  }

  public boolean hasFullCoords() {
    return coordX != null && coordY != null;
  }
}
