package com.researchspace.dao.hibernate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.researchspace.model.inventory.Instrument;
import java.nio.file.Path;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class RuntimeFieldTextSearchImplTest {

  @TempDir Path indexRoot;

  @Test
  void fallsBackWithoutOpeningASearchSessionWhenTheCompletenessMarkerIsMissing() {
    RuntimeFieldTextSearchImpl search = new RuntimeFieldTextSearchImpl();
    SessionFactory sessionFactory = mock(SessionFactory.class);
    search.setIndexRoot(indexRoot.toString());
    ReflectionTestUtils.setField(search, "enabled", true);
    ReflectionTestUtils.setField(search, "sessionFactory", sessionFactory);
    search.readCompletenessMarker();

    assertTrue(search.matchingIds(Instrument.class, "runtimeField", "hazard", 10).isEmpty());
    verifyNoInteractions(sessionFactory);
  }
}
