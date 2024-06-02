package com.researchspace.api.v1.controller;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiContainerInfo.ApiContainerGridLayoutConfig;
import com.researchspace.api.v1.model.ApiContainerLocationWithContent;
import com.researchspace.model.inventory.Container.ContainerType;
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

public class ContainerApiPostValidatorTest extends InventoryRecordValidationTestBase {

  public @Rule MockitoRule rule = MockitoJUnit.rule();

  @Autowired private ContainerApiPostValidator containerPostValidator;

  @Before
  public void setup() {
    validator = containerPostValidator;
  }

  @Test
  public void validateContainerName() {
    ApiContainer container = new ApiContainer(" ", ContainerType.LIST);
    Errors e = new BeanPropertyBindingResult(container, "fullpost");
    validator.validate(container, e);
    assertEquals(1, e.getErrorCount());
    assertFieldNameIs(e, "name");
    assertEquals("errors.required", e.getFieldError().getCode());

    container.setName(randomAlphabetic(BaseRecord.DEFAULT_VARCHAR_LENGTH + 1));
    e = resetErrorsAndValidate(container);
    assertEquals(1, e.getErrorCount());
    assertMaxLengthMsg(e);

    // sanity check
    container.setName(randomAlphabetic(BaseRecord.DEFAULT_VARCHAR_LENGTH));
    e = resetErrorsAndValidate(container);
    assertEquals(0, e.getErrorCount());
  }

  @Test
  public void validateGridConfig() {
    ApiContainer container = new ApiContainer("c1", ContainerType.LIST);
    Errors e = new BeanPropertyBindingResult(container, "fullpost");
    validator.validate(container, e);
    assertEquals(0, e.getErrorCount());

    // grid container config: both rows and columns number required
    ApiContainerGridLayoutConfig gridLayout = new ApiContainerGridLayoutConfig();
    container.setCType(ContainerType.GRID.toString());
    container.setGridLayout(gridLayout);
    e = resetErrorsAndValidate(container);
    assertEquals(2, e.getErrorCount());
    assertEquals(
        "errors.inventory.container.gridLayout.missingOptions", e.getFieldError().getCode());

    // just rows number
    gridLayout.setRowsNumber(2);
    e = resetErrorsAndValidate(container);
    assertEquals(2, e.getErrorCount());
    assertEquals(
        "errors.inventory.container.gridLayout.missingOptions", e.getFieldError().getCode());

    // invalid columns number (negative)
    gridLayout.setColumnsNumber(-2);
    e = resetErrorsAndValidate(container);
    assertEquals(1, e.getErrorCount());
    assertInvalidSize(e);

    // row and columns = 0
    gridLayout.setColumnsNumber(0);
    gridLayout.setRowsNumber(0);
    e = resetErrorsAndValidate(container);
    assertEquals(1, e.getErrorCount());
    assertInvalidSize(e);

    // invalid columns number (too large)
    gridLayout.setColumnsNumber(25);
    gridLayout.setRowsNumber(2);
    e = resetErrorsAndValidate(container);
    assertEquals(1, e.getErrorCount());
    assertInvalidSize(e);

    // both rows and columns set
    gridLayout.setColumnsNumber(2);
    e = resetErrorsAndValidate(container);
    assertEquals(0, e.getErrorCount());

    // grid container config: locations must match grid dimension
    List<ApiContainerLocationWithContent> locations = new ArrayList<>();
    ApiContainerLocationWithContent validGridLocation = new ApiContainerLocationWithContent(1, 2);
    locations.add(validGridLocation);
    ApiContainerLocationWithContent invalidGridLocation = new ApiContainerLocationWithContent(2, 3);
    locations.add(invalidGridLocation);
    container.setLocations(locations);
    e = resetErrorsAndValidate(container);
    assertEquals(1, e.getErrorCount());
    assertEquals("errors.inventory.location.outsideGridDimensions", e.getFieldError().getCode());
  }

  private void assertInvalidSize(Errors e) {
    assertEquals("errors.inventory.container.gridLayout.invalidSize", e.getFieldError().getCode());
  }

  @Test
  public void validateCanStoreFlagsName() {
    ApiContainer container = new ApiContainer("test container", ContainerType.LIST);
    Errors e = new BeanPropertyBindingResult(container, "fullpost");
    validator.validate(container, e);
    assertEquals(0, e.getErrorCount());

    // just one flag set is fine
    container.setCanStoreContainers(false);
    e = resetErrorsAndValidate(container);
    assertEquals(0, e.getErrorCount());

    container.setCanStoreSamples(false);
    e = resetErrorsAndValidate(container);
    assertEquals(1, e.getErrorCount());
    assertEquals("errors.inventory.container.invalidCanStoreFlags", e.getFieldError().getCode());
  }

  private Errors resetErrorsAndValidate(ApiContainer full) {
    Errors e = new BeanPropertyBindingResult(full, "fullpost");
    validator.validate(full, e);
    return e;
  }
}
