package com.researchspace.api.v1.controller;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.controller.ContainersApiController.ApiContainerPut;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiContainerInfo.ApiContainerGridLayoutConfig;
import com.researchspace.api.v1.model.ApiContainerLocationWithContent;
import com.researchspace.model.record.BaseRecord;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

public class ContainerApiPutValidatorTest extends InventoryRecordValidationTestBase {

  public @Rule MockitoRule rule = MockitoJUnit.rule();

  @Autowired private ContainerApiPutValidator containerPutValidator;

  @Before
  public void setup() {
    validator = containerPutValidator;
  }

  @Test
  public void validateIncomingContainerAlone() {

    // name field validation
    ApiContainer container = new ApiContainer();
    container.setName(" ");
    ApiContainerPut containerPut = new ApiContainerPut(container, null);

    Errors e = new BeanPropertyBindingResult(container, "fullput");
    validator.validate(containerPut, e);
    assertEquals(0, e.getErrorCount()); // name not required in put

    container.setName(randomAlphabetic(BaseRecord.DEFAULT_VARCHAR_LENGTH + 1));
    e = resetErrorsAndValidate(containerPut);
    assertEquals(1, e.getErrorCount());
    assertMaxLengthMsg(e); // but if provided and too long - should get an error

    // grid container config: both rows and columns number required
    ApiContainerGridLayoutConfig gridLayout = new ApiContainerGridLayoutConfig();
    container.setGridLayout(gridLayout);
    container.setName(null);
    e = resetErrorsAndValidate(containerPut);
    assertEquals(1, e.getErrorCount());
    assertEquals(
        "errors.inventory.container.gridLayout.missingOptions", e.getFieldError().getCode());
  }

  @Test
  public void validateIncomingGridContainerAgainstDBContainer() {
    ApiContainer dbContainer = new ApiContainer();
    dbContainer.setGridLayout(new ApiContainerGridLayoutConfig(2, 3));
    List<ApiContainerLocationWithContent> dbLocations = new ArrayList<>();
    ApiContainerLocationWithContent dbLocation = new ApiContainerLocationWithContent(2, 3);
    dbLocations.add(dbLocation);
    dbContainer.setLocations(dbLocations);

    ApiContainer incomingContainer = new ApiContainer();
    ApiContainerPut containerPut = new ApiContainerPut(incomingContainer, dbContainer);

    Errors e = new BeanPropertyBindingResult(incomingContainer, "fullpost");
    validator.validate(containerPut, e);
    assertEquals(0, e.getErrorCount());

    // grid container: new locations must match pre-existing grid dimension
    List<ApiContainerLocationWithContent> locations = new ArrayList<>();
    ApiContainerLocationWithContent validGridLocation = new ApiContainerLocationWithContent(1, 2);
    locations.add(validGridLocation);
    ApiContainerLocationWithContent invalidGridLocation = new ApiContainerLocationWithContent(2, 4);
    locations.add(invalidGridLocation);
    incomingContainer.setLocations(locations);
    e = resetErrorsAndValidate(containerPut);
    containerPutValidator.validateAgainstDbContainer(containerPut, e);
    assertEquals(1, e.getErrorCount());
    assertEquals("errors.inventory.location.outsideGridDimensions", e.getFieldError().getCode());

    // grid container: new dimensions must match pre-existing locations
    incomingContainer.getLocations().clear();
    incomingContainer.setGridLayout(new ApiContainerGridLayoutConfig(2, 2));
    e = resetErrorsAndValidate(containerPut);
    containerPutValidator.validateAgainstDbContainer(containerPut, e);
    assertEquals(1, e.getErrorCount());
    assertEquals("errors.inventory.location.outsideNewGridDimensions", e.getFieldError().getCode());
  }

  private Errors resetErrorsAndValidate(ApiContainerPut full) {
    Errors e = new BeanPropertyBindingResult(full.getIncomingContainer(), "fullput");
    containerPutValidator.validate(full, e);
    return e;
  }
}
