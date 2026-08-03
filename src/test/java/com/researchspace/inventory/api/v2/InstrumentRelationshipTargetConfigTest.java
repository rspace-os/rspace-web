package com.researchspace.inventory.api.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v2.resource.ApiV2RelationshipTargetSpec;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;

class InstrumentRelationshipTargetConfigTest {

  private final InstrumentEntityApiManager manager = mock(InstrumentEntityApiManager.class);
  private final ApiV2RelationshipTargetSpec<Instrument, Long> target =
      new InstrumentRelationshipTargetConfig(manager).instrumentApiV2RelationshipTarget();
  private final User actor = mock(User.class);

  @Test
  void resolvesAnInstrumentThroughTheExistingPermissionCheck() {
    Instrument instrument = new Instrument();
    instrument.setId(12L);
    when(manager.assertUserCanReadInstrument(12L, actor)).thenReturn(instrument);

    assertEquals(instrument, target.resolveReadable(12L, actor).orElseThrow().entity());
  }

  @Test
  void hidesMissingAndUnauthenticatedTargets() {
    when(manager.assertUserCanReadInstrument(12L, actor)).thenThrow(new NotFoundException());

    assertTrue(target.resolveReadable(12L, actor).isEmpty());
    assertTrue(target.resolveReadable(12L, null).isEmpty());
    verify(manager, never()).assertUserCanReadInstrument(12L, null);
  }
}
