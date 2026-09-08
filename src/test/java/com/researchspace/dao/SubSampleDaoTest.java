package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.model.User;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.testutils.SpringTransactionalTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The operations endpoint serialises concurrent work on one origin by locking its row through
 * {@link com.researchspace.dao.GenericDao#lockRowForUpdate} and then reads the values it computes
 * from as scalars under that lock (code review, findings 1 and 2). The lock's scope (one row, one
 * table, blocking a second connection) is only observable across transactions and is asserted in
 * {@code GenericDaoLockScopeIT}; the value behaviour of the scalar reads is pinned here.
 */
public class SubSampleDaoTest extends SpringTransactionalTest {

  private @Autowired SubSampleDao subSampleDao;

  private Long persistSubSampleHolding(String value, RSUnitDef unit) {
    User user = createAndSaveRandomUser();
    Container workbench = containerDao.getWorkbenchForUser(user);
    Sample sample = recordFactory.createSample("subsample scalar test", user);
    SubSample subSample = sample.getSubSamples().get(0);
    subSample.moveToNewParent(workbench);
    if (value == null) {
      subSample.setQuantity(null);
    } else {
      subSample.setQuantity(new QuantityInfo(new BigDecimal(value), unit.getId()));
    }
    Long subSampleId = sampleDao.persistNewSample(sample).getSubSamples().get(0).getId();
    // flush the insert, then clear, so the reads below hit the row rather than the session cache
    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();
    return subSampleId;
  }

  @Test
  public void getQuantityForUpdateReturnsTheStoredQuantity() {
    Long subSampleId = persistSubSampleHolding("5.5", RSUnitDef.MILLI_LITRE);

    QuantityInfo quantity = subSampleDao.getQuantityForUpdate(subSampleId);

    assertEquals(0, new BigDecimal("5.5").compareTo(quantity.getNumericValue()));
    assertEquals(Integer.valueOf(RSUnitDef.MILLI_LITRE.getId()), quantity.getUnitId());
  }

  @Test
  public void getQuantityForUpdateReturnsNullForMissingRowOrMissingQuantity() {
    // callers lock and 404-check the subsample first, so null unambiguously means "holds nothing"
    assertNull(subSampleDao.getQuantityForUpdate(-1L));
    assertNull(subSampleDao.getQuantityForUpdate(persistSubSampleHolding(null, null)));
  }

  @Test
  public void getActiveQuantitiesForUpdateReturnsTheSiblingQuantities() {
    User user = createAndSaveRandomUser();
    Container workbench = containerDao.getWorkbenchForUser(user);
    Sample sample = recordFactory.createSample("sibling scalar test", user);
    SubSample first = sample.getSubSamples().get(0);
    first.moveToNewParent(workbench);
    first.setQuantity(new QuantityInfo(new BigDecimal("2"), RSUnitDef.MILLI_LITRE.getId()));
    SubSample second = recordFactory.createSubSample("second sibling", user, sample);
    second.moveToNewParent(workbench);
    second.setQuantity(new QuantityInfo(new BigDecimal("3"), RSUnitDef.MILLI_LITRE.getId()));
    sample.getSubSamples().add(second);
    Long sampleId = sampleDao.persistNewSample(sample).getId();
    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    BigDecimal total =
        subSampleDao.getActiveQuantitiesForUpdate(sampleId, false).stream()
            .map(QuantityInfo::getNumericValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    assertEquals(0, new BigDecimal("5").compareTo(total));
  }
}
