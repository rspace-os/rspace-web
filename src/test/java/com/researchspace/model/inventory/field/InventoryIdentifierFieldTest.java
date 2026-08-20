package com.researchspace.model.inventory.field;

import static com.researchspace.model.inventory.field.InventoryIdentifierField.isValidDOI;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.Test;

public class InventoryIdentifierFieldTest {

  @Test
  public void testIsValidDOI() {
    assertTrue(isValidDOI("https://doi.org/10.12345/ety3h-cdd3k"));
    assertTrue(isValidDOI("http://doi.org/10.12345/ety3h-cdd3k"));
    assertTrue(isValidDOI("doi.org/10.12345/ety3h-cdd3k"));
    assertTrue(isValidDOI("10.12345/ety3h-cdd3k"));
    assertTrue(isValidDOI("10.1234/ety3h-cdd3k"));
    assertTrue(isValidDOI("10.12345/ety"));
    assertTrue(isValidDOI("10.12345/a"));
  }

  @Test
  public void testIsNotValidDOI() {
    assertFalse(isValidDOI("htts://doi.org/10.12345/ety3h-cdd3k"));
    assertFalse(isValidDOI("http//doi.org/10.12345/ety3h-cdd3k"));
    assertFalse(isValidDOI("do.org/10.12345/ety3h-cdd3k"));
    assertFalse(isValidDOI("11.12345/ety3h-cdd3k"));
    assertFalse(isValidDOI("10.123/ety"));
    assertFalse(isValidDOI("10.12345a"));
  }


}