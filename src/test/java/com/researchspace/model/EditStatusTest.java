package com.researchspace.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EditStatusTest {

  @BeforeEach
  public void setUp() throws Exception {}

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testIsEditable() {
    assertTrue(EditStatus.EDIT_MODE.isEditable());
    assertFalse(EditStatus.CANNOT_EDIT_OTHER_EDITING.isEditable());
    assertFalse(EditStatus.CANNOT_EDIT_NO_PERMISSION.isEditable());
    assertFalse(EditStatus.CAN_NEVER_EDIT.isEditable());
  }
}
