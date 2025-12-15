package com.researchspace.dao;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.model.User;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.service.inventory.ApiIdentifiersHelper;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DigitalObjectIdentifierDaoTest extends SpringTransactionalTest {

  @Autowired private DigitalObjectIdentifierDao daoUnderTest;
  @Autowired private ApiIdentifiersHelper doiHelper;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testGetActiveByStateAndCreator() throws IOException {
    // GIVEN
    User admin = createAndSaveAdminUser();
    User anotherUser = createAndSaveRandomUser();
    logoutAndLoginAs(admin);

    ObjectMapper mapper = new ObjectMapper();
    DataCiteDoi doiRegistered =
        mapper.readValue(
            IOUtils.resourceToString(
                "/TestResources/datacite/dataCiteDoi.json", StandardCharsets.UTF_8),
            DataCiteDoi.class);
    ApiInventoryDOI apiDoi = new ApiInventoryDOI(admin, doiRegistered);

    // WHEN
    DigitalObjectIdentifier doiSaved = doiHelper.createDoiToSave(apiDoi, admin);
    daoUnderTest.save(doiSaved);
    flushDatabaseState();

    // THEN
    assertTrue(daoUnderTest.getActiveIdentifiersByOwner(anotherUser).isEmpty());
    assertEquals(1, daoUnderTest.getActiveIdentifiersByOwner(admin).size());
  }
}
