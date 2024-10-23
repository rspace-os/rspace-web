package com.researchspace.api.v1.controller;

import com.researchspace.service.inventory.impl.SubSampleApiPostConfig;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class SubSampleApiPostConfigValidator extends InventoryRecordValidator implements Validator {

  @Override
  public boolean supports(Class<?> clazz) {
    return SubSampleApiPostConfig.class.isAssignableFrom(clazz);
  }

  @Override
  public void validate(Object target, Errors errors) {
    SubSampleApiPostConfig subSampleApiPostConfig = (SubSampleApiPostConfig) target;
    validateApiQuantityInfo(
        subSampleApiPostConfig.getSingleSubSampleQuantity(), "singleSubSampleQuantity", errors);
  }
}
