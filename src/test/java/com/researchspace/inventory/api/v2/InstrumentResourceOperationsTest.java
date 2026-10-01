package com.researchspace.inventory.api.v2;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.dao.ExtraFieldDao;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.inventory.InstrumentCustomFieldManager;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.service.inventory.InstrumentReadAccess;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InstrumentResourceOperationsTest {

  private final InstrumentEntityApiManager manager = mock(InstrumentEntityApiManager.class);
  private final InstrumentResourceOperations operations =
      new InstrumentResourceOperations(
          manager,
          mock(InstrumentReadAccess.class),
          mock(InstrumentCustomFieldManager.class),
          mock(ExtraFieldDao.class));
  private final User actor = mock(User.class);

  private static Instrument instrument(long id) {
    Instrument instrument = new Instrument();
    instrument.setId(id);
    return instrument;
  }

  @Test
  void readsOneInstrumentThroughTheExistingPermissionCheck() {
    Instrument instrument = instrument(12L);
    when(manager.findReadableInstrument(12L, actor)).thenReturn(Optional.of(instrument));

    assertSame(instrument, operations.findById(12L, actor).orElseThrow());
  }

  @Test
  void hidesADeletedInstrumentFromASingleRead() {
    Instrument instrument = instrument(12L);
    instrument.setRecordDeleted(true);
    when(manager.findReadableInstrument(12L, actor)).thenReturn(Optional.of(instrument));

    assertTrue(operations.findById(12L, actor).isEmpty());
  }
}
