package com.researchspace.dao;

import static com.researchspace.Constants.SYSADMIN_ROLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Booking catalogue text search ({@code q}) over instrument and parent-container names. */
class InstrumentBookingCatalogueSearchTest extends SpringTransactionalTest {

  @Autowired private InstrumentDao instrumentDao;

  private User owner;
  private User sysadmin;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    owner = createAndSaveUserIfNotExists(getRandomAlphabeticString("owner"));
    sysadmin = createAndSaveUserIfNotExists(getRandomAlphabeticString("sysadmin"), SYSADMIN_ROLE);
    initialiseContentWithEmptyContent(owner, sysadmin);
  }

  @Test
  void findsInstrumentWithoutParentLocationByNameAndDescription() throws Exception {
    String marker = getRandomAlphabeticString("parentless");
    Instrument parentless =
        instrumentDao.get(createBasicInstrumentForUser(owner, marker + " instrument").getId());
    parentless.setDescription(marker + " description");
    parentless.setParentLocation(null);
    instrumentDao.save(parentless);
    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    assertEquals(null, instrumentDao.get(parentless.getId()).getParentLocation());
    for (User caller : new User[] {owner, sysadmin}) {
      assertEquals(
          Set.of(parentless.getId()),
          instrumentDao.searchBookingCatalogueTargetIds(marker + " INSTRUMENT", caller));
      assertEquals(
          Set.of(parentless.getId()),
          instrumentDao.searchBookingCatalogueTargetIds(marker + " description", caller));
    }
  }

  @Test
  void findsInstrumentByReadableParentContainerName() throws Exception {
    String marker = getRandomAlphabeticString("room");
    Container parent =
        containerDao.get(createBasicContainerForUser(owner, marker + " container").getId());
    Instrument stored =
        instrumentDao.get(createBasicInstrumentForUser(owner, "stored instrument").getId());
    stored.moveToNewParent(parent);
    instrumentDao.save(stored);
    Instrument elsewhere =
        instrumentDao.get(createBasicInstrumentForUser(owner, "elsewhere instrument").getId());
    sessionFactory.getCurrentSession().flush();

    Set<Long> found = instrumentDao.searchBookingCatalogueTargetIds(marker, owner);

    assertEquals(Set.of(stored.getId()), found);
    assertTrue(
        instrumentDao
            .searchBookingCatalogueTargetIds("elsewhere instrument", owner)
            .contains(elsewhere.getId()));
  }
}
