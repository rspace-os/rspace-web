package com.axiope.userimport;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.model.dto.UserRegistrationInfo;
import com.researchspace.model.field.ErrorList;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class UserImportResultTest {

  @Test
  public void testUserImportResultNoNullArgs1() {
    var emptyUsers = Collections.<UserRegistrationInfo>emptyList();

    assertThrows(
        NullPointerException.class, () -> new UserImportResult(emptyUsers, null, null, null));
  }

  @Test
  public void testUserImportResultNoNullArgs2() {
    ErrorList errors = new ErrorList();

    assertThrows(NullPointerException.class, () -> new UserImportResult(null, null, null, errors));
  }

  @Test
  public void testUserImportResultGroupsCanBeNull() {
    UserImportResult result =
        new UserImportResult(Collections.emptyList(), null, null, new ErrorList());
    assertNotNull(result);
  }
}
