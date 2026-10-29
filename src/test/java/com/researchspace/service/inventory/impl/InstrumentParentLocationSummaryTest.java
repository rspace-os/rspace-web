package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.researchspace.dao.ContainerDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.model.inventory.InstrumentParentLocationSummary;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstrumentParentLocationSummaryTest {
  @Mock InstrumentDao instrumentDao;
  @Mock ContainerDao containerDao;
  @Mock User user;
  @InjectMocks InstrumentEntityApiManagerImpl manager;

  @Test
  void omitsUnreadableParentsAndPreservesReadableSummaries() {
    var readable = new InstrumentParentLocationSummary(10L, "Bench", ContainerType.WORKBENCH);
    var hidden = new InstrumentParentLocationSummary(20L, "Private", ContainerType.LIST);
    when(instrumentDao.getParentLocationSummaries(Set.of(1L, 2L)))
        .thenReturn(Map.of(1L, readable, 2L, hidden));
    when(containerDao.getReadableActiveContainerIds(Set.of(10L, 20L), user))
        .thenReturn(Set.of(10L));

    assertEquals(Map.of(1L, readable), manager.getParentLocationSummaries(Set.of(1L, 2L), user));
  }
}
