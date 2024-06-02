package com.researchspace.webapp.controller;

import static org.junit.Assert.assertFalse;

import com.researchspace.model.Organisation;
import com.researchspace.testutils.CommunityTestContext;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

@CommunityTestContext
public class OrganisationControllerTest extends SpringTransactionalTest {

  @Autowired private OrganisationController organisationController;

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void getApprovedOrganisationsTest() {

    List<Organisation> result = organisationController.getApprovedOrganisations("Yunnan").getData();
    assertFalse(result.isEmpty());

    result = organisationController.getApprovedOrganisations("Edinburgh").getData();
    assertFalse(result.isEmpty());

    result = organisationController.getApprovedOrganisations("University of Edinburgh").getData();
    assertFalse(result.isEmpty());
  }
}
