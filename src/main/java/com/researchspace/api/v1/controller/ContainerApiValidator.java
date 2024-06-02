package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiContainerInfo.ApiContainerGridLayoutConfig;
import com.researchspace.api.v1.model.ApiContainerLocationWithContent;
import java.util.List;
import org.apache.commons.lang.BooleanUtils;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public abstract class ContainerApiValidator extends InventoryRecordValidator implements Validator {

  private static final int GRID_MAX_DIMENSION = 24;

  @Override
  public boolean supports(Class<?> clazz) {
    return ApiContainer.class.isAssignableFrom(clazz);
  }

  protected void validateIncomingContainerFields(ApiContainer incomingContainer, Errors errors) {
    validateNameTooLong(incomingContainer.getName(), errors);
    validateDescriptionTooLong(incomingContainer.getDescription(), errors);
    validateTags(incomingContainer.getTags(), errors);
    validateInventoryRecordQuantity(incomingContainer, errors);
    validateExtraFields(incomingContainer, errors);
    validateGridContainerConfig(incomingContainer, errors);
    validateCanStoreFlags(incomingContainer, errors);
  }

  private void validateGridContainerConfig(ApiContainer incomingContainer, Errors errors) {
    ApiContainerGridLayoutConfig gridLayout = incomingContainer.getGridLayout();
    if (gridLayout != null) {
      Integer rowsNumber = gridLayout.getRowsNumber();
      Integer columnsNumber = gridLayout.getColumnsNumber();
      if (rowsNumber == null || columnsNumber == null) {
        errors.rejectValue("gridLayout", "errors.inventory.container.gridLayout.missingOptions");
        return;
      }
      if (columnsNumber < 0
          || columnsNumber > GRID_MAX_DIMENSION
          || rowsNumber < 0
          || rowsNumber > GRID_MAX_DIMENSION) {
        errors.rejectValue(
            "gridLayout",
            "errors.inventory.container.gridLayout.invalidSize",
            new Object[] {columnsNumber, rowsNumber},
            "wrongSize");
      }
      // check incoming locations match grid layout config
      List<ApiContainerLocationWithContent> incomingLocations = incomingContainer.getLocations();
      if (incomingLocations != null) {
        for (ApiContainerLocationWithContent newLocation : incomingLocations) {
          Integer coordX = newLocation.getCoordX();
          Integer coordY = newLocation.getCoordY();
          if (!isLocationWithinGridDimensions(coordX, coordY, columnsNumber, rowsNumber)) {
            errors.rejectValue(
                "locations",
                "errors.inventory.location.outsideGridDimensions",
                new Object[] {coordX, coordY, columnsNumber, rowsNumber},
                "outsideGrid");
          }
        }
      }
    }
  }

  protected boolean isLocationWithinGridDimensions(
      Integer coordX, Integer coordY, Integer columnsNumber, Integer rowsNumber) {
    if (coordX == null && coordY == null) {
      return true; // must incoming location that doesn't have explicit coordinates set
    }
    if (coordX == null || coordY == null) {
      return false; // this is an error situation, either both or none should be set.
    }
    return coordX > 0 && coordX <= columnsNumber && coordY > 0 && coordY <= rowsNumber;
  }

  private void validateCanStoreFlags(ApiContainer incomingContainer, Errors errors) {
    Boolean canStoreSamples = incomingContainer.getCanStoreSamples();
    Boolean canStoreContainers = incomingContainer.getCanStoreContainers();
    if (BooleanUtils.isFalse(canStoreSamples) && BooleanUtils.isFalse(canStoreContainers)) {
      errors.rejectValue("canStoreSamples", "errors.inventory.container.invalidCanStoreFlags");
      return;
    }
  }
}
