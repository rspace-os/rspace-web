package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.controller.ContainersApiController.ApiContainerPut;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiContainerInfo.ApiContainerGridLayoutConfig;
import com.researchspace.api.v1.model.ApiContainerLocationWithContent;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@Component
public class ContainerApiPutValidator extends ContainerApiValidator {

  @Override
  public boolean supports(Class<?> clazz) {
    return clazz.isAssignableFrom(ApiContainerPut.class);
  }

  @Override
  public void validate(Object target, Errors errors) {
    ApiContainerPut containersToPut = (ApiContainerPut) target;
    validateIncomingContainerFields(containersToPut.getIncomingContainer(), errors);
  }

  public void validateAgainstDbContainer(ApiContainerPut containers, Errors errors) {
    ApiContainer incomingContainer = containers.getIncomingContainer();
    ApiContainer dbContainer = containers.getDbContainer();

    ApiContainerGridLayoutConfig incomingLayout = incomingContainer.getGridLayout();
    ApiContainerGridLayoutConfig dbLayout = dbContainer.getGridLayout();
    List<ApiContainerLocationWithContent> incomingLocations = incomingContainer.getLocations();
    List<ApiContainerLocationWithContent> dbLocations = dbContainer.getLocations();

    // check that incoming locations match pre-existing grid layout
    if (incomingLocations != null && incomingLayout == null && dbLayout != null) {
      Integer dbColumnsNumber = dbLayout.getColumnsNumber();
      Integer dbRowsNumber = dbLayout.getRowsNumber();
      for (ApiContainerLocationWithContent newLocation : incomingLocations) {
        Integer coordX = newLocation.getCoordX();
        Integer coordY = newLocation.getCoordY();
        if (!isLocationWithinGridDimensions(coordX, coordY, dbColumnsNumber, dbRowsNumber)) {
          errors.rejectValue(
              "locations",
              "errors.inventory.location.outsideGridDimensions",
              new Object[] {coordX, coordY, dbColumnsNumber, dbRowsNumber},
              "outsideGrid");
        }
      }
    }
    // check that incoming grid config is valid with pre-existing locations
    if (incomingLayout != null && dbLocations.size() > 0) {
      Integer incomingColumnsNumber = incomingLayout.getColumnsNumber();
      Integer incomingRowsNumber = incomingLayout.getRowsNumber();
      for (ApiContainerLocationWithContent dbLocation : dbLocations) {
        Integer coordX = dbLocation.getCoordX();
        Integer coordY = dbLocation.getCoordY();
        if (!isLocationWithinGridDimensions(
            coordX, coordY, incomingColumnsNumber, incomingRowsNumber)) {
          errors.rejectValue(
              "locations",
              "errors.inventory.location.outsideNewGridDimensions",
              new Object[] {coordX, coordY, incomingColumnsNumber, incomingRowsNumber},
              "outsideNewGrid");
        }
      }
    }
  }
}
