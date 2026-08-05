package com.researchspace.api.v1.controller;

import com.ibm.icu.text.ListFormatter;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.service.ListFormatUtils;
import java.util.List;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;

@Component
public class ContainerApiPostValidator extends ContainerApiValidator {

  @Override
  public void validate(Object target, Errors errors) {
    ValidationUtils.rejectIfEmptyOrWhitespace(
        errors,
        "name",
        "errors.required",
        new Object[] {new DefaultMessageSourceResolvable("label.nameLowercase")},
        null);
    validateIncomingContainerFields((ApiContainer) target, errors);
    validateNewGridConfigOptions((ApiContainer) target, errors);
  }

  private void validateNewGridConfigOptions(ApiContainer target, Errors errors) {
    if (target.getCType() == null) {
      errors.rejectValue(
          "cType",
          "errors.inventory.container.typeRequired",
          new Object[] {
            ListFormatUtils.formatList(
                List.of(
                    ContainerType.LIST.name(),
                    ContainerType.GRID.name(),
                    ContainerType.IMAGE.name()),
                ListFormatter.Type.OR)
          },
          null);
      return;
    }
    // if we want a grid, a valid grid layout is present
    if (ContainerType.GRID.name().toLowerCase().equals(target.getCType().toLowerCase())) {
      if (target.getGridLayout() == null) {
        errors.rejectValue("gridLayout", "errors.inventory.container.gridLayoutRequired");
      } else if (target.getGridLayout().getColumnsNumber() == null
          || target.getGridLayout().getColumnsNumber() == 0
          || target.getGridLayout().getRowsNumber() == null
          || target.getGridLayout().getRowsNumber() == 0) {
        errors.rejectValue(
            "gridLayout",
            "errors.inventory.container.gridLayoutInvalidSize",
            new Object[] {0, 0},
            null);
      }
    }
  }
}
