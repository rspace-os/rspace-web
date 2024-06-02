package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.model.inventory.Container.ContainerType;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;

@Component
public class ContainerApiPostValidator extends ContainerApiValidator {

  @Override
  public void validate(Object target, Errors errors) {
    ValidationUtils.rejectIfEmptyOrWhitespace(
        errors, "name", "errors.required", new Object[] {"name"}, "name is required");
    validateIncomingContainerFields((ApiContainer) target, errors);
    validateNewGridConfigOptions((ApiContainer) target, errors);
  }

  private void validateNewGridConfigOptions(ApiContainer target, Errors errors) {
    if (target.getCType() == null) {
      errors.rejectValue(
          "cType",
          "errors.required",
          new Object[] {"Container type (cType) (LIST, GRID or IMAGE)"},
          "cTypeRequired");
      return;
    }
    // if we want a grid, a valid grid layout is present
    if (ContainerType.GRID.name().toLowerCase().equals(target.getCType().toLowerCase())) {
      if (target.getGridLayout() == null) {
        errors.rejectValue(
            "gridLayout",
            "errors.required",
            new Object[] {"Grid layout must be set for a grid container"},
            "Grid layout required");
      } else if (target.getGridLayout().getColumnsNumber() == null
          || target.getGridLayout().getColumnsNumber() == 0
          || target.getGridLayout().getRowsNumber() == null
          || target.getGridLayout().getRowsNumber() == 0) {
        errors.rejectValue(
            "gridLayout",
            "errors.inventory.container.gridLayout.invalidSize",
            new Object[] {0, 0},
            "Valid grid size required");
      }
    }
  }
}
