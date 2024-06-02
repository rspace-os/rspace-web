package com.axiope.userimport;

import static org.junit.Assert.assertNotNull;

import com.researchspace.model.dto.UserRegistrationInfo;
import com.researchspace.model.field.ErrorList;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class UserImportResultTest {

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test(expected = IllegalArgumentException.class)
  public void testUserImportResultNoNullArgs1() {
    new UserImportResult(Collections.<UserRegistrationInfo>emptyList(), null, null, null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUserImportResultNoNullArgs2() {
    new UserImportResult(null, null, null, new ErrorList());
  }

  @Test
  public void testUserImportResultGroupsCanBeNull() {
    UserImportResult result =
        new UserImportResult(
            Collections.<UserRegistrationInfo>emptyList(), null, null, new ErrorList());
    assertNotNull(result);
  }
}
