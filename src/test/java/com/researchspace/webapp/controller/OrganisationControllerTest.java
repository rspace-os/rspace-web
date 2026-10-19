package com.researchspace.webapp.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.researchspace.model.Organisation;
import com.researchspace.testutils.CommunityTestContext;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@CommunityTestContext
public class OrganisationControllerTest extends SpringTransactionalTest {

  @Autowired private OrganisationController organisationController;

  @BeforeEach
  public void setup() throws Exception {
    super.setUp();
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void getApprovedOrganisationsTest() {

    List<Organisation> result = organisationController.getApprovedOrganisations("Yunnan").getData();
    assertThat(result).isNotEmpty();

    result = organisationController.getApprovedOrganisations("Edinburgh").getData();
    assertThat(result).isNotEmpty();

    result = organisationController.getApprovedOrganisations("University of Edinburgh").getData();
    assertThat(result).isNotEmpty();
  }
}
