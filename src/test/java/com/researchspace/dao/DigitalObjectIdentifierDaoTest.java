package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.model.User;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import com.researchspace.service.inventory.ApiIdentifiersHelper;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DigitalObjectIdentifierDaoTest extends SpringTransactionalTest {

  @Autowired private DigitalObjectIdentifierDao daoUnderTest;
  @Autowired private ApiIdentifiersHelper doiHelper;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

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

  @Test
  public void findActiveByIdentifierAndTypeMatchesValueAndTypeAndSkipsDeleted() {
    User admin = createAndSaveAdminUser();
    logoutAndLoginAs(admin);
    String handle = "21.11157/44b18238-bba1-4b42-abcc-975017181420";
    ApiInventoryDOI apiDoi = new ApiInventoryDOI();
    apiDoi.generatePublicLinkSuffix();
    apiDoi.setDoi(handle);
    apiDoi.setDoiType(IdentifierType.PIDINST_B2INST.name());
    apiDoi.setState("accepted");
    apiDoi.setLinked(true);
    DigitalObjectIdentifier saved = daoUnderTest.save(doiHelper.createDoiToSave(apiDoi, admin));
    flushDatabaseState();

    assertTrue(
        daoUnderTest
            .findActiveByIdentifierAndType(handle, IdentifierType.PIDINST_B2INST)
            .isPresent());
    assertTrue(
        daoUnderTest
            .findActiveByIdentifierAndType(handle, IdentifierType.PIDINST_DATACITE)
            .isEmpty());
    assertTrue(
        daoUnderTest
            .findActiveByIdentifierAndType("21.11157/other", IdentifierType.PIDINST_B2INST)
            .isEmpty());

    saved.setDeleted(true);
    daoUnderTest.save(saved);
    flushDatabaseState();
    assertTrue(
        daoUnderTest
            .findActiveByIdentifierAndType(handle, IdentifierType.PIDINST_B2INST)
            .isEmpty());
  }

  /**
   * The batch form annotates a whole page of provider hits in one query, so it has to agree with
   * the single-identifier form on every axis that one filters: value, type and soft deletion.
   */
  @Test
  public void findActiveByIdentifiersAndTypeMatchesTheSameRowsAsTheSingleLookup() {
    User admin = createAndSaveAdminUser();
    logoutAndLoginAs(admin);
    String linked = "21.11157/aaaaaaaa-0000-0000-0000-000000000001";
    String alsoLinked = "21.11157/bbbbbbbb-0000-0000-0000-000000000002";
    String softDeleted = "21.11157/cccccccc-0000-0000-0000-000000000003";
    String onDataCite = "10.1234/dddddddd";
    savePidinstIdentifier(admin, linked, IdentifierType.PIDINST_B2INST);
    savePidinstIdentifier(admin, alsoLinked, IdentifierType.PIDINST_B2INST);
    DigitalObjectIdentifier deleted =
        savePidinstIdentifier(admin, softDeleted, IdentifierType.PIDINST_B2INST);
    savePidinstIdentifier(admin, onDataCite, IdentifierType.PIDINST_DATACITE);
    deleted.setDeleted(true);
    daoUnderTest.save(deleted);
    flushDatabaseState();

    List<DigitalObjectIdentifier> found =
        daoUnderTest.findActiveByIdentifiersAndType(
            List.of(linked, alsoLinked, softDeleted, onDataCite, "21.11157/never-stored"),
            IdentifierType.PIDINST_B2INST);

    assertEquals(
        List.of(linked, alsoLinked),
        found.stream().map(DigitalObjectIdentifier::getIdentifier).sorted().toList(),
        "the soft-deleted row, the other registry's row and the unknown value are all skipped");
    assertTrue(
        daoUnderTest
            .findActiveByIdentifiersAndType(List.of(), IdentifierType.PIDINST_B2INST)
            .isEmpty(),
        "an empty input must not reach the database as an empty IN list");
  }

  private DigitalObjectIdentifier savePidinstIdentifier(
      User owner, String pid, IdentifierType type) {
    ApiInventoryDOI apiDoi = new ApiInventoryDOI();
    apiDoi.generatePublicLinkSuffix();
    apiDoi.setDoi(pid);
    apiDoi.setDoiType(type.name());
    apiDoi.setState("accepted");
    apiDoi.setLinked(true);
    return daoUnderTest.save(doiHelper.createDoiToSave(apiDoi, owner));
  }
}
