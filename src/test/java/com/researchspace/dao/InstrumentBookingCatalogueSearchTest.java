package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InstrumentBookingCatalogueSearchTest extends SpringTransactionalTest {
  private final BookingInstrumentQueryDao instrumentDao;

  @Autowired
  InstrumentBookingCatalogueSearchTest(BookingInstrumentQueryDao instrumentDao) {
    this.instrumentDao = instrumentDao;
  }

  @Test
  void treatsLikeCharactersAsLiteralCatalogueSearchText() {
    User owner = createInitAndLoginAnyUser();
    String marker = getRandomAlphabeticString("literal");
    var literal = createBasicInstrumentForUser(owner, marker + "%_!\\");
    createBasicInstrumentForUser(owner, marker + "ordinary");
    sessionFactory.getCurrentSession().flush();

    for (String character : new String[] {"%", "%_", "%_!", "%_!\\"}) {
      assertEquals(
          Set.of(literal.getId()),
          instrumentDao.searchBookingCatalogueTargetIds(marker + character, owner));
    }
  }
}
